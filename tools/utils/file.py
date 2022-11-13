import time
import os
import logging
import pathlib
from .api import HubitatAPIWrapper
from .enums import bcolors, STATUS_COLOR
from .source_watchdog import watch_folder_for_changes
from .manifest import *


class HubitatFileManager(object):
    def __init__(self, api: HubitatAPIWrapper, package):
        self.api = api
        self.targets = self.setup_target_map(package)
        self.package = package

    def update_package(self, apps=True, drivers=True):
        print(f"""{bcolors.HEADER}STARTING UPLOAD{bcolors.ENDC}""")
        return self.do_on_package(self.update_latest_version, apps, drivers)

    def retrieve_package(self, apps=True, drivers=True):
        print(f"""{bcolors.HEADER}STARTING RETRIEVE{bcolors.ENDC}""")
        return self.do_on_package(self.retrieve_latest_source, apps, drivers)

    def do_on_package(self, op_func, apps=True, drivers=True):
        if apps:
            for app in self.targets.apps:
                op_func(app)

        if drivers:
            for driver in self.targets.drivers:
                op_func(driver)

    def get_local_and_remote_source(self, src: PackageFile):
        info = self.api.get_info(src)[0] if src.code_id else {}
        source = self.get_source(src.source_path)
        return source, info.get("source"), info

    def update_latest_version(self, src: PackageFile):
        source_local, source_hubitat, hubitat_src_info = self.get_local_and_remote_source(src)
        current_version = hubitat_src_info.get('version')
        name = hubitat_src_info.get('name')
        display_id_name = f"{src.code_id} {name}"
        source = self.get_source(src.source_path)
        if source == hubitat_src_info.get('source'):
            print(f"{bcolors.OKCYAN}SKIPPED{bcolors.ENDC}: {src.file_type.value.title()} {display_id_name} v{current_version} is the same as {src.source_path}")
        else:
            print(f"{bcolors.BOLD}UPDATING:{bcolors.ENDC} {src.file_type.value.title()} {display_id_name} v{current_version} with {src.source_path}")
            result = self.api.update_code(src, source, current_version)
            status_code = result[1].status_code
            result_json = result[0]
            new_version = result_json.get('version')
            status = result_json['status']
            status_color = STATUS_COLOR.get(status)
            print(f"{status_color}{status.upper()}{bcolors.ENDC}: Update of {src.file_type.value.title()} {display_id_name} v{new_version} {status_code}:")
            if status == 'error':
                error_message = result_json.get('errorMessage').replace('\n', '')
                print(f"\t{status_color}{src.source_path}{bcolors.ENDC}")
                print(f"\t\t{status_color}{error_message}{bcolors.ENDC}")
            return result

    def retrieve_latest_source(self, src):
        if src.code_id:
            info = self.api.get_info(src)[0]
            current_version = info.get('version')
            name = info.get('name')
            display_id_name = f"{src.code_id} {name}"
            source = self.get_source(src.source_path)
            if source == info.get('source'):
                print(f"{src.file_type.value.title()} {display_id_name} v{current_version} is the same as {src.source_path}: SKIPPED")
            else:
                print(f"Updating {src.source_path} with {src.file_type.value.title()} {display_id_name} v{current_version} fom Hubitat")
                self.save_source(src.source_path, info.get('source'))
        else:
            print(f"{src.file_type.value.title()} {src.source_path} Not found on Hubitat: SKIPPED")


    def get_source(self, source_path):
        return pathlib.Path(source_path).read_text()

    def save_source(self, source_path, source):
        with open(source_path, "w") as f:
            source_code = f.write(source)
        return source_code

    def auto_upload(self):
        print(f"""{bcolors.HEADER}STARTING AUTO-UPLOAD{bcolors.ENDC}""")
        logging.basicConfig(level=logging.INFO,
                            format='%(asctime)s - %(message)s',
                            datefmt='%Y-%m-%d %H:%M:%S')
        observer = watch_folder_for_changes('.', self)
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            observer.stop()
        observer.join()

    def setup_target_map(self, app):
        pm = AppPackageManifest()
        for root, directories, files in os.walk(app):
            if not directories:
                code_type = CodeType(root.replace(f"{app}/", '').replace('s', ''))
                for file in files:
                    if file.endswith('.groovy'):
                        code = self.api.get_code_by_filename(file, code_type)
                        code = code.get('id') if code else code
                        cf = PackageFile(code_id=code,
                                         source_path=os.path.join(root, file),
                                         file_type=code_type)
                        pm.add_file(cf)
        return pm

