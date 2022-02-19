from dotenv import load_dotenv
import sys
import os
import requests
load_dotenv()
from enum import Enum
import webbrowser


class CodeType(Enum):
    APP = "app"
    DRIVER = "driver"


class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


"""WARNING!!! You must update these ids and set put hubitat ip address in a .env file to use this"""
TARGETS = {
    "MoenFloManager": {
        "apps": [
            {"id": 827, "source_path": "MoenFloManager/apps/MoenDeviceManager.groovy"},
            {"id": 828, "source_path": "MoenFloManager/apps/MoenLocationInstance.groovy"},
            {"id": 829, "source_path": "MoenFloManager/apps/MoenSmartShutoffInstance.groovy"},
            {"id": 830, "source_path": "MoenFloManager/apps/MoenSmartWaterDetectorInstance.groovy"}
        ],
        "drivers": [
            {"id": 1604, "source_path": "MoenFloManager/drivers/MoenLocation.groovy"},
            {"id": 1606, "source_path": "MoenFloManager/drivers/MoenSmartShutoff.groovy"},
            {"id": 1605, "source_path": "MoenFloManager/drivers/MoenSmartWaterDetector.groovy"}
        ]
    }
}

ip = os.getenv('HUBITAT')
base_url = f"http://{ip}"


def update_package(target_name, apps=True, drivers=True):
    print(f"""Starting Upload to Hubitat""")
    return do_on_package(update_latest_version, target_name, apps, drivers)


def retrieve_package(target_name, apps=True, drivers=True):
    print(f"""Starting Retrieve from Hubitat""")
    return do_on_package(retrieve_latest_source, target_name, apps, drivers)


def do_on_package(op_func, target_name, apps=True, drivers=True):
    target = TARGETS.get(target_name)
    if apps:
        for app in target.get("apps"):
            op_func(app['id'], app['source_path'], CodeType.APP.value)

    if drivers:
        for driver in target.get('drivers'):
            op_func(driver['id'], driver['source_path'], CodeType.DRIVER.value)


def update_latest_version(id, source_path, type):
    info = get_info(id, type)[0]
    current_version = info.get('version')
    name = info.get('name')
    display_id_name = ' '.join([str(x) for x in [id, name] if x])
    source = get_source(source_path)
    if source == info.get('source'):
        print(f"{type.title()} {display_id_name} v{current_version} is the same as {source_path}: SKIPPED")
    else:
        print(f"Updating {type.title()} {display_id_name} v{current_version} with {source_path}")
        result = update_code(id, source, current_version, type)
        status_code = result[1].status_code
        result_json = result[0]
        new_version = result_json.get('version')
        status = f"{result_json['status']}: {result_json['errorMessage']}" if result_json.get('status') == 'error' else result_json['status']
        print(f"Update of {type.title()} {display_id_name} v{new_version} {status_code}: {status}")
        return result


def retrieve_latest_source(id, source_path, type):
    info = get_info(id, type)[0]
    current_version = info.get('version')
    name = info.get('name')
    display_id_name = ' '.join([str(x) for x in [id, name] if x])
    source = get_source(source_path)
    if source == info.get('source'):
        print(f"{type.title()} {display_id_name} v{current_version} is the same as {source_path}: SKIPPED")
    else:
        print(f"Updating {source_path} with {type.title()} {display_id_name} v{current_version} fom Hubitat")
        save_source(source_path, info.get('source'))


def get_source(source_path):
    with open(source_path) as f:
        source_code = f.read()
    return source_code


def save_source(source_path, source):
    with open(source_path, "w") as f:
        source_code = f.write(source)
    return source_code


def update_code(id, source_code, version, type):
    url = f"{base_url}/{type}/ajax/update"
    body = {
        "id": id,
        "source": source_code,
        "version": version
    }
    r = requests.post(url, data=body)
    return r.json(), r


def get_info(id, type):
    url = f"{base_url}/{type}/ajax/code?id={id}"
    r = requests.get(url)
    return r.json(), r


def open_all(pkg):
    for app in TARGETS.get(pkg)['apps']:
        open_in_hubitat_code_editor(CodeType.APP, app['id'])
    for driver in TARGETS.get(pkg)['drivers']:
        open_in_hubitat_code_editor(CodeType.DRIVER, driver['id'])


def open_in_hubitat_code_editor(type, id):
    url = f"""{base_url}/{type.value}/editor/{id}"""
    webbrowser.open(url)


if len(sys.argv) > 1:
    package_name = sys.argv[1]
    direction = 'upload' if len(sys.argv) == 2 and sys.argv[2] == 'upload' else 'retrieve'
else:
    package_name = os.getenv('TARGET')
    direction = os.getenv('DIRECTION')



if __name__ == "__main__":
    if not package_name:
        print("Warning! you must update the app id/driver ids to match your device.")
    else:
        if direction == 'upload':
            update_package(package_name)
        else:
            retrieve_package(package_name)



