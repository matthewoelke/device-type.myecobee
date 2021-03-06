/**
 *  ecobeeChangeMode
 *
 *  Copyright 2014 Yves Racine
 
 *  Developer retains all right, title, copyright, and interest, including all copyright, patent rights, trade secret 
 *  in the Background technology. May be subject to consulting fees under the Agreement between the Developer and the Customer. 
 *  Developer grants a non exclusive perpetual license to use the Background technology in the Software developed for and delivered 
 *  to Customer under this Agreement. However, the Customer shall make no commercial use of the Background technology without
 *  Developer's written consent.
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *
 * Change the mode manually (by pressing the app's play button) and automatically at the ecobee thermostat(s)
 * If you need to set it for both Away and Home modes, you'd need to save them as 2 distinct apps
 * Don't forget to set the app to run only for the target mode.
 */
definition(
	name: "ecobeeChangeMode",
	namespace: "yracine",
	author: "Yves Racine",
	description:
	"Change the mode manually (by pressing the app's play button) and automatically at the ecobee thermostat(s)",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee@2x.png"
)

preferences {


	page(name: "selectThermostats", title: "Thermostats", install: false , uninstall: true, nextPage: "selectProgram") {
		section("About") {
			paragraph "ecobeeChangeMode, the smartapp that sets your ecobee thermostat to a given program/climate ['Away', 'Home', 'Night']" + 
                		" based on ST hello mode."
			paragraph "Version 1.9.1" 
			paragraph "Copyright©2014 Yves Racine"
				href url:"http://github.com/disconn3ct/device-type.myecobee", style:"embedded", required:false, title:"More information..."  
					description: "http://github.com/disconn3ct/device-type.myecobee/blob/master/README.md"
		}
		section("Change the following ecobee thermostat(s)...") {
			input "thermostats", "device.myEcobeeDevice", title: "Which thermostat(s)", multiple: true
		}
	}
	page(name: "selectProgram", title: "Ecobee Programs", content: "selectProgram")
	page(name: "Notifications", title: "Notifications Options", install: true, uninstall: true) {
		section("Notifications") {
			input "sendPushMessage", "enum", title: "Send a push notification?", metadata: [values: ["Yes", "No"]], required:
				false
			input "phone", "phone", title: "Send a Text Message?", required: false
		}
        section([mobileOnly:true]) {
			label title: "Assign a name for this SmartApp", required: false
		}
	}
}


def selectProgram() {
    def ecobeePrograms = thermostats[0].currentClimateList.toString().minus('[').minus(']').tokenize(',')
	log.debug "programs: $ecobeePrograms"
	def enumModes=[]
	location.modes.each {
		enumModes << it.name
	}    

	return dynamicPage(name: "selectProgram", title: "Select Ecobee Program", install: false, uninstall: true, nextPage:
			"Notifications") {
		section("Select Program") {
			input "givenClimate", "enum", title: "Change to this program?", options: ecobeePrograms, required: true
		}
		section("When SmartThings' hello home mode changes to (ex. 'Away', 'Home')[optional]") {
			input "newMode", "enum", options: enumModes, multiple:true, required: false
		}

        
	}
}


def installed() {
	subscribe(location, changeMode)
	subscribe(app, changeMode)
}

def updated() {
	unsubscribe()
	subscribe(location, changeMode)
	subscribe(app, changeMode)
}


def changeMode(evt) {
	def message

	Boolean foundMode=false        
	newMode.each {
        
		if (it==location.mode) {
			foundMode=true            
		}            
	}        
        
	if ((newMode != null) && (!foundMode)) {
        
		log.debug "changeMode>location.mode= $location.mode, newMode=${newMode},foundMode=${foundMode}, not doing anything"
		return			
	}

	message = "ecobeeChangeMode>setting the thermostat(s) to $givenClimate.."
	send(message)

	thermostats?.setThisTstatClimate(givenClimate)



}


private send(msg) {
	if (sendPushMessage != "No") {
		log.debug("sending push message")
		sendPush(msg)
	}
	if (phone) {
		log.debug("sending text message")
		sendSms(phone, msg)
	}

	log.debug msg
}
