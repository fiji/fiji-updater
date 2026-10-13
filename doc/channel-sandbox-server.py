"""
Serves a sandbox of update sites for manually testing the Fiji Updater.

Everything under the sandbox directory is served as-is, so indexes, channel
manifests and anything uploaded into the sandbox come from there. A request
for a blob the sandbox does not have -- a path ending in a 14-digit timestamp
-- under a proxied prefix is fetched from the production host instead, and
streamed through without being stored. Production is only ever read.

That is what lets a sandbox core site start from a copy of the production
index, a few hundred kilobytes, rather than from every file Fiji ships.

Note: nothing but blobs is proxied. An index or channels.txt missing from the
sandbox is a 404, never production's, so the sandbox cannot silently start
reflecting a change made on the real site.

Usage: uv run python channel-sandbox-server.py <sandbox-dir> [<port>]
"""

import functools
import http.server
import os
import re
import shutil
import sys
import urllib.error
import urllib.request

# Sandbox path prefix -> production URL prefix it stands in for.
UPSTREAM = {
    "/Fiji/": "https://sites.fiji.sc/Fiji/",
}

BLOB = re.compile(r"-\d{14}$")


class SandboxHandler(http.server.SimpleHTTPRequestHandler):

    def send_head(self):
        path = self.path.split("?", 1)[0].split("#", 1)[0]
        if os.path.exists(self.translate_path(path)):
            return super().send_head()
        upstream = self.upstream_url(path)
        if upstream is None:
            self.send_error(404, "Not in sandbox")
            return None
        try:
            response = urllib.request.urlopen(upstream, timeout=60)
        except urllib.error.HTTPError as e:
            self.send_error(e.code, "Upstream: " + str(e.reason))
            return None
        except (urllib.error.URLError, OSError) as e:
            self.send_error(502, "Upstream unreachable: " + str(e))
            return None
        self.log_message("proxied %s", upstream)
        self.send_response(200)
        for header in ("Content-Type", "Content-Length", "Last-Modified"):
            value = response.headers.get(header)
            if value is not None:
                self.send_header(header, value)
        self.end_headers()
        return response

    def copyfile(self, source, outputfile):
        shutil.copyfileobj(source, outputfile, 65536)

    @staticmethod
    def upstream_url(path):
        if not BLOB.search(path):
            return None
        for prefix, upstream in UPSTREAM.items():
            if path.startswith(prefix):
                return upstream + path[len(prefix):]
        return None


def main(args):
    if not 1 <= len(args) <= 2:
        sys.exit(__doc__.strip().splitlines()[-1])
    root = os.path.abspath(args[0])
    port = int(args[1]) if len(args) > 1 else 8765
    handler = functools.partial(SandboxHandler, directory=root)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", port), handler)
    print(f"Serving {root} at http://127.0.0.1:{port}/")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main(sys.argv[1:])
