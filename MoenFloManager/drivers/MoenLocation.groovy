/**
 * Moen Flo Manager for Hubitat By David Manuel
 * Licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
 * Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF 
 * ANY KIND, either express or implied. See the License for the specific language governing permissions and 
 * limitations under the License.
 */

 

metadata {
    definition (name: "Moen FLO Location", namespace: "dacmanj", author: "David Manuel") {
        capability "PushableButton"
        capability "LocationMode"
        capability "Polling"

        command "home"
        command "away"
        command "sleepMode"
        command "reset"
        command "push", ["Button Number*"]

        attribute "mode", "enum", ["home","away","sleep"]
        attribute "updated", "string"
        attribute "lastEvent", "string"
        attribute "lastEventDetail", "string"
        attribute "lastEventDateTime", "string"
        attribute "totalConsumptionToday", "number"

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
    log.debug "device installed()"
    configure()
}

def unschedulePolling() {
    unschedule()
}

def schedulePolling() {
    unschedule()
    if (parent?.pollingInterval) {
        schedule(parent.getCronString(), poll)
    }
}

def poll() {
    if (parent?.logEnable) log.debug("Polling Moen")
    getLocationInfo()
    getConsumption()
    parent.updateDeviceAndAppName()
}

def home() {
    setMode("home")
}

def away() {
    setMode("away")
}

def sleepMode() {
    setMode("sleep")
}

def setMode(mode) {
    locationNickname = device.getDataValue("locationNickname")
    if (parent?.logEnable) log.debug "Setting Flo ${locationNickname} mode to ${mode}"
    def locationId = device.getDataValue("locationId")
    def uri = "/locations/${locationId}/systemMode"
    def body = [target:mode]
    if (mode == "sleep") {
        body.put("revertMinutes", parent?.revertMinutes)
        body.put("revertMode", parent?.revertMode)
    }
    def response = parent.makeAPIPost(uri, body, "Mode Update", [204])
    sendEvent(name: "mode", value: mode)
}

def push(btn) {
    locationNickname = device.getDataValue("locationNickname")
    if (parent?.logEnable) log.debug "${locationNickname} Button pushed: ${btn}"
    switch(btn as Integer) {
       case 1: mode = "home"; break;
       case 2: mode = "away"; break;
       case 3: mode = "sleep"; break;
    }
    if(mode) {
        if (parent?.logEnable) log.debug "Setting Flo ${locationNickname} mode to ${mode} via button press"
        setMode(mode)
    }
    else {
        if (parent?.logEnable) log.debug "Ignoring invalid button press ${btn} sent to ${locationNickname}. Use button 1 to set to home, 2 to set to away or 3 to sleep for ${parent?.revertMinutes} minutes."
    }
}


def getLocationInfo() {
  def locationId = device.deviceNetworkId.substring(0,36)
  if (parent?.logEnable) log.debug "Getting location data for: ${locationId}"
  def location = parent.getLocationData(locationId)
  device.updateDataValue("locationNickname", location?.nickname)
  device.updateDataValue("locationAddress", location?.address)
  device.updateDataValue("locationId", locationId)
  def system_mode = location?.systemMode?.target
  sendEvent(name: "mode", value: system_mode)
}

def round(d, places = 2) {
    if (d || d == 0) {
        return (d as double).round(places)
    }
    else {
        return d
    }
}

def getConsumption() {
  def locationId = device.getDataValue("locationId")
  def startDate = new Date().format('yyyy-MM-dd') + 'T00:00:00.000'
  def endDate = new Date().format('yyyy-MM-dd') + 'T23:59:59.999'
  def uri = "/water/consumption?startDate=${startDate}&endDate=${endDate}&locationId=${locationId}&interval=1h"
  log.debug(uri)
  def response = parent.makeAPIGet(uri, "Get Consumption")
  def data = response.data
  def totalConsumptionToday = data?.aggregations?.sumTotalGallonsConsumed;
  if (device.currentValue('totalGallonsToday') >=0) {
    sendEvent(name: "totalGallonsToday", value: round(totalConsumptionToday, 2))
  }
  if (parent.getUnits() == "metric"){
    totalConsumptionToday = totalConsumptionToday * 3.78541;
  }
  if (totalConsumptionToday) {
    sendEvent(name: "totalConsumptionToday", value: round(totalConsumptionToday, 2))
  }
  
}

def configure() {
  def locationId = device.deviceNetworkId.substring(0,36)
  getLocationInfo()
  sendEvent(name:"numberOfButtons", value: 3)
  schedulePolling()
  state.configured = true
}

