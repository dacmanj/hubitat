from .session_factory import SessionWrapper
from .enums import CodeType
from .utils import AppListHTMLParser
from .manifest import *
import webbrowser


class HubitatAPIWrapper(object):
    def __init__(self, session: SessionWrapper):
        self.s = session
        self._apps = None
        self._drivers = None

    def update_code(self, f: PackageFile, source_code, version):
        if f.code_id:
            url = f"/{f.file_type.value}/ajax/update"
        else:
            return self.create_file(f, source_code)
        body = {
            "id": f.code_id or '',
            "source": source_code,
            "version": version or ''
        }
        r = self.s.post(url, data=body)
        return r.json(), r

    def create_file(self, f: PackageFile, source_code):
        url = f"/{f.file_type.value}/save"
        body = {
            "id": '', "version": '',
            "source": source_code
        }
        r = self.s.post(url, data=body)
        body['id'] = r.url.split('/')[-1]
        body['status'] = 'OK' if r.ok else 'FAILED'
        f.code_id = body['id'] if body['id'] else f.code_id
        return self.get_info(f), r

        from urllib.parse import urlsplit


    def get_info(self, f: PackageFile):
        if f.code_id:
            url = f"/{f.file_type.value}/ajax/code?id={f.code_id}"
            r = self.s.get(url)
            return r.json(), r
        else:
            return {}

    def open_all(self, package_manifest):
        for app in package_manifest.apps:
            self.open_in_hubitat_code_editor(app)
        for driver in package_manifest.drivers:
            self.open_in_hubitat_code_editor(driver)

    def open_in_hubitat_code_editor(self, f: PackageFile):
        url = f"""{self.s.prefix_url}/{f.file_type.value}/editor/{f.code_id}"""
        webbrowser.open(url)

    def get_code_by_filename(self, name: str, typ: CodeType):
        name = name.replace('.groovy', '')
        if typ == CodeType.APP:
            code_files = self.apps
        elif typ == CodeType.DRIVER:
            code_files = self.drivers
        else:
            raise InvalidCodeTypeException
        try:
            return [c for c in code_files if c.get('name', '').replace('FLO ', '').replace(' ', '') == name][0]
        except IndexError:
            return None

    @property
    def drivers(self):
        if not self._drivers:
            self._drivers = self.s.get("/driver/list/data").json()
        return self._drivers

    @property
    def apps(self):
        if self._apps:
            apps = self._apps
        else:
            parser = AppListHTMLParser()
            parser.feed(self.s.get("/app/list").text)
            apps = [{parser.headers[i].lower().replace(' ', ''): v for i, v in enumerate(a)} for a in parser.values]
            self._apps = apps
        return apps


class InvalidCodeTypeException(Exception):
    pass
