package ge.maestro.callblocker

import android.telecom.Call
import android.telecom.CallScreeningService

class CallBlockService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val response = CallResponse.Builder()
        val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val rule = if (incoming && number.isNotBlank()) Matcher.find(number, Store.rules(this)) else null
        if (rule != null) {
            response.setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true)
            Store.addHistory(this, Blocked(number, System.currentTimeMillis(), rule.pattern, rule.type))
        }
        respondToCall(details, response.build())
    }
}
