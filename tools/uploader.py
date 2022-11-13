from utils import create_hubitat_session, HubitatAPIWrapper, HubitatFileManager
from dotenv import load_dotenv
import sys
import os
load_dotenv()

if __name__ == "__main__":
    auto_upload = False

    if len(sys.argv) > 1:
        package_name = sys.argv[1]
        direction = 'upload' if len(sys.argv) == 3 and sys.argv[2] == 'upload' else 'retrieve'
    else:
        package_name = os.getenv('TARGET')
        direction = os.getenv('DIRECTION')
        auto_upload = os.getenv('AUTOUPLOAD').lower() == 'true'

    ip = os.getenv('HUBITAT')
    s = create_hubitat_session(ip)
    api = HubitatAPIWrapper(s)
    hfm = HubitatFileManager(api, package_name)

    if not package_name:
        print("Warning! No package name found, please specify")
    else:
        if direction == 'upload':
            print("direction upload")
            if auto_upload:
                print("starting auto upload")
                hfm.update_package()
                hfm.auto_upload()
            else:
                hfm.update_package()
        else:
            hfm.retrieve_package()
