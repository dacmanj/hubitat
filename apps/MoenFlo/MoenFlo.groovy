definition(
    name: "Moen Flo",
    namespace: "dacmanj",
    author: "David Manuel",
    description: "Device creator for Moen Flo Driver",
    category: "General",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: true
)

preferences {
  page(name: "mainPage")
  page(name: "deviceInstaller")
}

import groovy.transform.Field
@Field static final String childNamespace = "dacmanj" // namespace of child device drivers
@Field static final Map driverMap = [
   "flo_device_v2":       "Moen Flo Shutoff Valve",
   "DEFAULT":             "Moen Flo Shutoff Valve"
]

def mainPage() {
  log.debug getAllChildDevices()[0]?.id
  if (!state.authenticated) {
    authenticate()
    return deviceInstaller()
  } else {
    return loginPage()
  }
}

def loginPage() {
    if (state.authenticated) {
        return deviceInstaller()
    }
    dynamicPage(name: "mainPage", title: "Manage Your Moen Flo Devices", install: true, uninstall: true) {
        section("Credentials") {
            preferences {        
              input(name: "username", type: "string", title:"User Name", description: "User Name", required: true, displayDuringSetup: true)
              input(name: "password", type: "password", title:"Password", description: "Password", displayDuringSetup: true)
              input(name: "btnLogin", type: "button", title: "Login")
            }
        }
    }
}

def installed() {
  log.debug "installed"
  initialize()
}

def updated() {
  log.debug "updated"
  initialize()
}

def initialize() {
  log.debug "initialize"
  authenticate()
  if (state.token) {
    log.debug("login succeeded")
    getUserInfo()
  }
  unschedule()
  //runEvery5Minutes(checkDevices)
}

def logout() {
    state.token = null
    state.authenticated = false
    state.devicesCache = null
    state.locationsCache = null
    state.userData = null
    log.debug "logout()"
    loginPage()
}

def uninstalled() {
  log.debug "uninstalled"
}

def checkDevices() {
  log.debug "checkDevices"
}

def deviceInstaller() {
    if (!state.authenticated) {
        return loginPage()
    }
    getUserInfo()
    discoverDevices()
    def locations = state.userData.locations
    def deviceOptions = [:]
    def installedDeviceIds = getIdsOfInstalledDevices();
    locations.each { location ->
        location.devices.each { dev ->
            if (!installedDeviceIds.find {it == dev.id} ) {                
            //TODO: sort and/or filter by types of devices
                def value = "${location.nickname} - ${dev.nickname}"
                def key = "${dev.id}"
                deviceOptions["${key}"] = value
            }
        }
    }
    def numFound = deviceOptions.size()
    state.deviceOptions = deviceOptions.sort { it.value }

    dynamicPage(name: "deviceInstaller", title: "", nextPage: null, uninstall: true) {
        section("") {
            input "devicesToInstall", "enum", required: true, title: "Select a device to install  (${numFound} installable found)", multiple: true, options: deviceOptions
            input(name: "btnInstallDevice", type: "button", title: "Install Device")
        }
        section("Installed Devices") {
            //todo: list of devices
            paragraph("To remove a device -- go to that device and click \"Remove Device\"")
            paragraph(displayListOfInstalledDevices())
        }
        section("") {
            input(name: "btnLogout", type: "button", title: "Logout")
        }
    }
}

def getIdsOfInstalledDevices() {
    return getChildDevices().collect{ dev ->
        return dev.deviceNetworkId
    }
}

def displayListOfInstalledDevices() {
    return "<ul>"+getChildDevices().sort({ a, b -> a["deviceNetworkId"] <=> b["deviceNetworkId"] }).collect {
        "<li><a href='/device/edit/$it.id'>$it.label</a></li>"
    }.join("\n")+"</ul>"
}

void createNewSelectedDevices() {
  log.debug "starting install..."
  log.debug settings["devicesToInstall"]
  log.debug getChildDevices()

   settings["devicesToInstall"].each { deviceId ->
      def childDevice = getChildDevice(deviceId)
      device = state.devicesCache[deviceId]
      if (!childDevice) {
        if (device) {
          try {
            log.debug "Creating new device for ${device.deviceType} ${device.nickname}"
            String devDriver = driverMap[device.deviceType] ?: driverMap["DEFAULT"]
            log.debug "Driver: ${devDriver}"
            String devDNI = "${device?.id}"
            Map devProps = [
              name: (device?.nickname), label: (device?.nickname)
            ]
            childDevice = addChildDevice(childNamespace, devDriver, devDNI, devProps)
          } catch (Exception ex) {
            log.error("Unable to create new device for ${device.id}: $ex")
          }
        }
      else {
        log.error("Unable to create new device for ${device.nickname} ${device.id}")
      }
    }

   if (!childDevice) {
       log.debug("Failed to setup device ${deviceId}")
   }

   }
   app.removeSetting("devicesToInstall")
}


def authenticate() {
    log.debug("authenticate()")
    def uri = "https://api.meetflo.com/api/v1/users/auth"
    if (!password) {
        log.error("Login Failed: No password")
    } else {
        def body = [username:username, password:password]
        def headers = [:]
        headers.put("Content-Type", "application/json")

        try {
            httpPostJson([headers: headers, uri: uri, body: body]) { response -> def msg = response?.status
                if (logEnable) log.debug("Login received response code ${response?.status}")
                    if (response?.status == 200) {
                        msg = "Success"
                        state.token = response.data.token
                        state.user_id = response.data.tokenPayload.user.user_id
                        state.authenticated = true
                    }
                    else {
                        log.error "Login Failed: (${response.status}) ${response.data}"
                        state.authenticated = false
                    }
              }
        }
        catch (Exception e) {
            log.error "Login exception: ${e}"
            log.error "Login Failed: Please confirm your Flo Credentials"
            state.authenticated = false
        }
    }
}

def getUserInfo() {
  def user_id = state.user_id
  def uri = "https://api-gw.meetflo.com/api/v2/users/${user_id}?expand=locations,alarmSettings"
  def response = make_authenticated_get(uri, "Get User Info")
  state.userData = response.data
}

def discoverDevices() {
  def locations = []
  Map devicesCache = [:]
  Map locationsCache = [:]
  def userLocations = state.userData.locations
  if(userLocations) {
    userLocations.each { location ->
      def locationDetail = getLocationData(location.id)
      def devices = locationDetail?.devices
      if (devices) {
        locations.add(locationDetail)
        devices.each{ d ->
          devicesCache << [(d.id): (d)]
        }
        locationsCache << [(locationDetail.id): (locationDetail)]
      }
    }
  } else {
    log.debug "No locations in user data"
  }
  state.userData.locations = locations
  state.devicesCache = devicesCache
  state.locationsCache = locationsCache
}

def getLocationData(locationId) {
  def uri = "https://api-gw.meetflo.com/api/v2/locations/${locationId}?expand=devices"
  def response = make_authenticated_get(uri, "Get Location Info")
  return response.data
}

def getDeviceData(deviceId) {
  def uri = "https://api-gw.meetflo.com/api/v2/devices/${deviceId}"
  def response = make_authenticated_get(uri, "Get Device")
  return response.data
}

def getLastDeviceAlert(deviceId) {
  def uri = "https://api-gw.meetflo.com/api/v2/alerts?isInternalAlarm=false&deviceId=${deviceId}"
  def response = make_authenticated_get(uri, "Get Alerts")
  return response.data.items
}



def make_authenticated_get(uri, request_type, success_status = [200, 202]) {
    def token = state.token
    if (!token || token == "") authenticate();
    def response = [:];
    int max_tries = 2;
    int tries = 0;
    while (!response?.status && tries < max_tries) {
        def headers = [:]
        headers.put("Content-Type", "application/json")
        headers.put("Authorization", token)

        try {
            httpGet([headers: headers, uri: uri]) { resp -> def msg = ""
                if (logEnable) log.debug("${request_type} Received Response Code: ${resp?.status}")
                if (resp?.status in success_status) {
                    response = resp;
                }
                else {
                    log.error "${request_type} Failed (${response.status}): ${response.data}"
                }
              }
        }
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage().contains("Forbidden") || e.getMessage().contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized Exception... Refreshing token..."
                //authenticate()
            }
        }
        tries++

    }
    return response
}

def make_authenticated_post(uri, body, request_type, success_status = [200, 202]) {
    def token = state.token
    if (!token || token == "") authenticate();
    def response = [:];
    int max_tries = 2;
    int tries = 0;
    while (!response?.status && tries < max_tries) {
        def headers = [:]
        headers.put("Content-Type", "application/json")
        headers.put("Authorization", token)

        try {
            httpPostJson([headers: headers, uri: uri, body: body]) { resp -> def msg = ""
                if (logEnable) log.debug("${request_type} Received Response Code: ${resp?.status}")
                if (resp?.status in success_status) {
                    response = resp;
                }
                else {
                    log.debug "${request_type} Failed (${resp.status}): ${resp.data}"
                }
            }
        }
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage().contains("Forbidden") || e.getMessage().contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized Exception... Refreshing token..."
                //authenticate()
            }
        }
        tries++

    }
    return response
}


void appButtonHandler(btn) {
   switch(btn) {
      case "btnLogout":
         logout()
         break
      case "btnLogin":
         authenticate()
         deviceInstaller()
         break
      case "btnLocationRefresh":
         discoverDevices()
         break
      case "btnInstallDevice":
         createNewSelectedDevices()
         break
      default:
         log.warn "Unhandled app button press: $btn"
   }
}
