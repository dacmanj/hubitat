# Moen FLO Device Manager
This [Hubitat](https://hubitat.com/) app includes drivers for the [Moen Flo Smart Shutoff valve and Smart Water Detector](https://meetflo.com/).

## Installation
[Hubitat Package Manager](https://github.com/dcmeglio/hubitat-packagemanager) is recommended for installing the App.

When selecting the package to install either search by keyword for "Moen FLO" and selecting "Moen FLO Device Manager by David Manuel." You can also use "From a URL" and provide the manifest url directly (https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/packageManifest.json).

The best source of support for the driver is in the Hubitat Community in this [thread](https://community.hubitat.com/t/moen-flo-virtual-device/9677).

After you've installed Moen FLO Device Manager, you must go to the ***Moen FLO Device Manager*** app (in Apps), login and add devices. Follow the prompts to login to your Moen FLO account and then either:

1. Click "Setup All Devices" to autodiscover your devices and locations
2. Create individual devices by clicking "Add Shutoff Valve," "Add Water Detector" or "Add Location"

Location devices represent "locations in your Moen Flo account. This "virtual device" controls/reports your presence status (e.g. Home/Sleep/Away) and also reports a daily consumption running total.

### Manual Installation

If not using Hubitat Package Manager, the groovy files in the ["apps" folder](https://github.com/dacmanj/hubitat/tree/main/MoenFloManager/apps) should be imported into the "Apps Code" tab and the ["drivers" folder](https://github.com/dacmanj/hubitat/tree/main/MoenFloManager/drivers) into the "Drivers Code" tab.

1. For each groovy file in the ["apps" folder](https://github.com/dacmanj/hubitat/tree/main/MoenFloManager/apps) import the app code as described in steps 1-5 of [How to Install Custom Apps](https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Apps) You do not need to follow the steps under "Enabling OAuth" in the app installation guide.
2. For each groovy file in the ["drivers" folder](https://github.com/dacmanj/hubitat/tree/main/MoenFloManager/drivers) complete the steps under "Installing custom drivers" in the [How to Install Custom Drivers](https://docs.hubitat.com/index.php?title=How_to_Install_Custom_Drivers) for each of the files. The app manages the devices, so you do not need to complete the steps under "Loading your custom driver."
3. Once all the apps and driver files have been installed, go to the "Apps" tab and click "Add User App"
4. Select Moen FLO Device Manager in the list
5. Configure the app as described in ["Installation"](#Installation) above


## Usage Notes
- All data is obtained by polling the Moen API periodically. The frequency is set in the device settings in the device's app.
- As a consequence of polling, it is not recommended to rely on the timing of the Moen water detectors to close the Moen valve. Moen's native capabilities will do that in near real time, but the device manager would take up to the entire polling interval to send a signal from a moen device. I built the integration so that I could use a bunch of existing zigbee leak detectors with the smart shutoff valve.
- The shutoff valve capability works just like any other valve and can be opened or closed by HSM (e.g. when water is detected)
- The mode (home, away and sleep) can be set using the device buttons or by "pushing" one of the three buttons in an automation rule. Button 1 is for "Home" mode, Button 2 is for "Away" mode, and Button 3 is for "Sleep" mode. You can also enable "Set this FLO Location to Away when Hubitat Safety Montor set to Armed Away" on the location device to automatically set Away based on Hubitat Safety Monitor's away mode. It will revert to "Home" when HSM disarms.
- The API requires a timeout value for "Sleep" mode to return to another mode. The default is to return to Home mode after 120 minutes, but can be changed in the location settings.
- The Manual Health Test button performs a manual health test. The last result for a hubitat initiated health test will appear in the attributes on the next poll after the test is complete.
## License
Moen FLO Device Manager by David Manuel is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0).
Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

This project is not affiliated with, endorsed or sponsored by Moen Inc nor Flo Technologies, Inc.

All trademarks are reserved to their respective owners.
## Release Notes
- 2022-07-16 - v1.0.13
  - Fix release note date from v1.0.12
  - Remove stray debug log statement from location device driver

- 2022-07-11 - v1.0.12
  - Add option to vary start minute so that all sync isn't happening every x minutes past the hour
  - Add option to set the device name and app using template values from the location/device data from the API.
     - See the "Gear" icon in the device app instance to see all the data -- in the deviceInfo variable for the device or location variable for location.
     - Only top level values are currently supported e.g. deviceType, but not connectivity.ssid
     - The variable must appear inside brackets following a '$' e.g. ${deviceType} to be replaced.
     - The name will dynamically update itself if the values change
     - The device label can be manually configured in the device settings and is not affected by this feature

- 2022-02-27 - v1.0.10
  - Add random polling start minute option in main app to reduce load on Hubitat/Moen API. 
    - Original behavior would do all updates on the hour then again each polling interval. 
    - When random start time is enabled it will pick a random starting minute per device. 
  - Scheduled jobs are now run from the device app instance and will no longer be visible on the device page (see app settings / the gear icon on the corresponding device) 

- 2022-02-26 - v1.0.9
  - Fix quoting of template string on refresh log message (displaying ${remainingMinutes} instead of value)
  - Update location device driver to fix PushableButton capability

- 2022-02-19 - v1.0.5-8
  - Add null safe operator to device cache location ids to prevent null errors
  - Add option to keep log on always
  - Add python CI tool to repo
  - Add pre-emptive refresh of access token
  - Add reset token, reset cache buttons to app
  - Fix bug in get/post in 1.0.6
  - Fix typo in auth successful message
  
- 2022-02-12 - v1.0.4
  - Better handling of invalid credentials / login error
  - Better logging for initial setup
  - Fix state/credential handling in logout
  - Remove last alert api call from smart detector

- 2021-12-25 - v1.0.3  
  - Add HSM Away sync on Location
  - Add metric support to water detector
  - Updates to UI text for clarity on Device Manager
- 2021-12-18 - v1.0.2
  - Add metric support
- 2021-10-06 - v1.0.1  
  - Initial Release of App - Forked standalone driver moved API and configuration to App, add multi-location support, merge in smart water detector code from [jlaughter](https://github.com/jlaughter)
