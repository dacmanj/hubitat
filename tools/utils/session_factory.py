from requests import Session
from urllib.parse import urljoin


class SessionWrapper(Session):
    def __init__(self, prefix_url=None, *args, **kwargs):
        super(SessionWrapper, self).__init__(*args, **kwargs)
        self.prefix_url = prefix_url

    def request(self, method, url, *args, **kwargs):
        url = urljoin(self.prefix_url, url)
        return super(SessionWrapper, self).request(method, url, *args, **kwargs)


def create_hubitat_session(hub_url):
    if not hub_url.startswith('http://') and not hub_url.startswith('https://'):
        hub_url = f"http://{hub_url}"
    return SessionWrapper(hub_url.rstrip('/'))