# Moen FLO Device Manager

## License
Moen FLO Device Manager by David Manuel is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0).
Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

## Release Notes
 *  2021-12-18  v1.0.2  Add metric support
 *  2021-10-06  v1.0.1  Initial Release of App - Forked standalone driver moved API and configuration to App

## Installation
[Hubitat Package Manager](https://github.com/dcmeglio/hubitat-packagemanager) is recommended for installing the App.

When selecting the package to install either search by keyword for "Moen FLO" and selecting "Moen FLO Device Manager by David Manuel." You can also use "From a URL" and provide the manifest url directly (https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/packageManifest.json).

The best source of support for the driver is in the Hubitat Community in this [thread](https://community.hubitat.com/t/moen-flo-virtual-device/9677).

After you've installed Moen FLO Device Manager, you must go to the ***Moen FLO Device Manager*** app (in Apps), login and add devices. Follow the prompts to login to your Moen FLO account and then either:

1. Click "Setup All Devices" to autodiscover your devices and locations
2. Create individual devices by clicking "Add Shutoff Valve," "Add Water Detector" or "Add Location"

Location devices represent "locations in your Moen Flo account. This "virtual device" controls/reports your presence status (e.g. Home/Sleep/Away) and also reports a daily consumption running total.

## Usage Notes
- All data is obtained by polling the Moen API periodically. The frequency is set in the device settings in the device's app.
- As a consequence of polling, it is not recommended to rely on the timing of the moen water detectors to act -- e.g. anything th
- The shutoff valve capability works just like any other valve and can be opened or closed by HSM (e.g. when water is detected)
- The mode (home, away and sleep) can be set using the device buttons or by "pushing" one of the three buttons in an automation rule. Button 1 is for "Home" mode, Button 2 is for "Away" mode, and Button 3 is for "Sleep" mode.
- The API requires a timeout value for "Sleep" mode to return to another mode. The default is to return to Home mode after 120 minutes, but can be changed in the location settings.
- The Manual Health Test button performs a manual health test. The last result for a hubitat initiated health test will appear in the attributes on the next poll after the test is complete.
