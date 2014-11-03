package com.rx201.apkmon_demo;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

public class SMSReceiver extends BroadcastReceiver {
	static {
		android.util.Log.i("apihook", "************SMSReceiver.<cinit>********");
	}

/*TODO: Bug - because the Broadcast Receiver is instantiated at a late stage of
 * message handling, we cannot hook in time to intercept this binder call. 
*/
	@Override
	public void onReceive(Context context, Intent intent) {
	       if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
	            StringBuilder buf = new StringBuilder();
	            Bundle bundle = intent.getExtras();
	            if (bundle != null) {
	        		Object messages[] = (Object[]) bundle.get("pdus");
	                for (int i = 0; i < messages.length; i++) {
	                    SmsMessage message = SmsMessage.createFromPdu((byte[]) messages[i]);
	                    buf.append("Received SMS from  ");
	                    buf.append(message.getDisplayOriginatingAddress());
	                    buf.append(" - ");
	                    buf.append(message.getDisplayMessageBody());
	                }
	            }
	            Log.i("APIHook", "onReceiveIntent: " + buf);
	            NotificationManager nm = (NotificationManager) context.getSystemService(
	                    Context.NOTIFICATION_SERVICE);

	            nm.notify(123, new Notification(0, "SMSRecv: " + buf.toString(), 0));
	            Context ctx = context.getApplicationContext();
				Toast.makeText(ctx, "SMS Recv: " + buf.toString(), Toast.LENGTH_SHORT).show();
	        }
	}

}
