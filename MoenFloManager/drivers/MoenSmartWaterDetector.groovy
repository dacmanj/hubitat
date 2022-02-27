/**
 * Moen Flo Manager for Hubitat By David Manuel
 *   with contributions from Jeffrey Laughter (https://raw.githubusercontent.com/jlaughter/hubitat-moenflo/master/moenflodetector.groovy)
 * Licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
 * Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */


metadata {
    definition (name: "Moen FLO Smart Water Detector", namespace: "dacmanj", author: "David Manuel") {
        capability "RelativeHumidityMeasurement"
        capability "TemperatureMeasurement"
        capability "WaterSensor"
        capability "Battery"
        capability "Polling"
        capability "SignalStrength"

        command "reset"

        attribute "updated", "string"
        attribute "ssid", "string"
        attribute "lastEvent", "string"
        attribute "lastEventDetail", "string"
        attribute "lastEventDateTime", "string"
    }

}

def reset() {
    state.clear()
    unschedule()
    configure()
}

def updated() {
    configure()
    if (state.configured) poll()
}

def installed() {
    log.debug "start device installed()"
    configure()
}

def poll() {
    if (logEnable) log.debug("Polling Moen")
    getDeviceInfo()
}

def getDeviceInfo() {
    def deviceId = device.deviceNetworkId.substring(0,36)
    if (parent?.logEnable) log.debug "Getting device data for: ${deviceId}"
    def deviceInfo = parent.getDeviceData(deviceId)
    def location = parent.getLocationsCache()[deviceInfo?.location?.id]
    device.updateDataValue("locationNickname", location?.nickname)
    device.updateDataValue("locationAddress", location?.address)
    device.updateDataValue("deviceId", deviceInfo?.id)
    device.updateDataValue("locationId", deviceInfo?.location?.id)
    device.updateDataValue("deviceNickname", deviceInfo?.nickname)
    device.updateDataValue("deviceType", deviceInfo?.deviceType)
    device.updateDataValue("deviceModel", deviceInfo?.deviceModel)
    device.updateDataValue("firmwareVersion", deviceInfo?.fwVersion)


    def deviceTemperature = deviceInfo?.telemetry?.current?.tempF
    if (parent.getUnits() == "imperial") {
        sendEvent(name: "temperature", value: round(deviceTemperature, 2), unit: "F")
    } else {
        deviceTemperature = round(fahrenheitToCelsius(deviceTemperature), 2)
        sendEvent(name: "temperature", value: deviceTemperature, unit: "C")
    }
    sendEvent(name: "humidity", value: round(deviceInfo?.telemetry?.current?.humidity, 0), unit: "%rh")
    sendEvent(name: "battery", value: round(deviceInfo?.battery?.level, 0), unit: "%")
    def water_state = deviceInfo?.fwProperties?.telemetry_water
    def WATER_STATES = [1: "wet", 2: "dry"]
    if (water_state) {
        sendEvent(name: "water", value: WATER_STATES[1])}
    else { sendEvent(name: "water", value: WATER_STATES[2])}
    sendEvent(name: "updated", value: deviceInfo?.telemetry?.current?.updated)
    sendEvent(name: "rssi", value: deviceInfo?.fwProperties?.telemetry_rssi)
    sendEvent(name: "ssid", value: deviceInfo?.fwProperties?.wifi_sta_ssid)


}

def round(d, places = 2) {
    try { return (d as double).round(places) }
    catch (Exception e) { return (null) }
}

def configure() {
    unschedule()
    poll()
    state.configured = true
}
