from .enums import CodeType


class PackageFile(object):
    code_id: str = None
    source_path: str = None
    file_type: CodeType = None

    def __init__(self, code_id: str = None, source_path: str = None, file_type: CodeType = None):
        args = {"code_id": code_id, "source_path": source_path, "file_type": file_type}
        for k, v in args.items():
            if hasattr(self, k):
                setattr(self, k, v)


class InvalidManifestError(Exception):
    pass


class AppPackageManifest(object):
    apps: list[PackageFile] = []
    drivers: list[PackageFile] = []

    def add_file(self, file: PackageFile):
        if file.file_type == CodeType.APP:
            self.apps.append(file)
        elif file.file_type == CodeType.DRIVER:
            self.drivers.append(file)
        else:
            raise InvalidManifestError


class PackageManifest(object):
    apps: list[AppPackageManifest] = []
