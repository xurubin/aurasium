package com.rx201.asm;

import com.rx201.apkmon.permissions.PermissionRequestIntent;

import android.app.IntentService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

public class PermissionCheck extends IntentService {
	public PermissionCheck() {
		super("PermissionCheck");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		Log.i("PermissionCheck", "onHandleIntent");
		PermissionRequestIntent pri = new PermissionRequestIntent(intent);
		if (pri.validate()) {
			PermissionHandler handler = PermissionHandlerFactory.create(this, pri, PolicyStorage.get(this).getPolicy(pri));
			handler.handlePermissionCheck(pri);
		}
	}

}

class PermissionHandlerFactory {
	private static final Class[] handlers = new Class[] {
		GrantPermissionHandler.class,
		DenyPermissionHandler.class,
		KillAppPermissionHandler.class,
		UserConsentHandler.class
	};
	public static final String[] handlerDescriptions = new String[] {
		"Allow",
		"Deny",
		"Kill Application",
		"Ask User",
	};
	public static String DecisionToDescription(String remembered_decision) {
		for (int i=0; i<handlers.length; i++)
			if(handlers[i].getName().equals(remembered_decision))
				return handlerDescriptions[i];
		return "Unknown";
	}
	public static String DescriptionToDecision(String desc) {
		for (int i=0; i<handlerDescriptions.length; i++)
			if(handlerDescriptions[i].equals(desc))
				return handlers[i].getName();
		return null;
	}
	
	public static final String ALWAYS_ALLOW = "allow_always";
	public static final String ALWAYS_DENY  = "deny_always";
	public static final String ALWAYS_KILL  = "kill_always";
	public static final String ALWAYS_ASK   = "ask_always";
	
	public static PermissionHandler create(Context context, PermissionRequestIntent intent, String remembered_decision) {
		PermissionHandler handler = null;
		for(Class handlerClass : handlers)
		{
			if (handlerClass.getName().equals(remembered_decision))
			{
				try {
					handler = (PermissionHandler)handlerClass.newInstance();
				} catch (InstantiationException e) {
					handler = null;
				} catch (IllegalAccessException e) {
					handler = null;
				}
			break;
			}
		}
		if (handler == null)
			handler = new UserConsentHandler();
//			if (remembered_decision.equals(ALWAYS_ALLOW))
//				handler = new GrantPermissionHandler();
//			else if (remembered_decision.equals(ALWAYS_DENY))
//				handler = new DenyPermissionHandler();
//			else if (remembered_decision.equals(ALWAYS_KILL))
//				handler = new KillAppPermissionHandler();
//			else if (remembered_decision.equals(ALWAYS_ASK))
//				handler = new UserConsentHandler();
//			else
//				handler = new UserConsentHandler();
		handler.setContext(context);
		return handler;
	}
	
}
abstract class PermissionHandler {
	protected Context context;
	public PermissionHandler setContext(Context context) {
		this.context = context;
		return this;
	}
	public void updatePolicyStorage(PermissionRequestIntent intent) {
		PolicyStorage.get(context).setPolicy(intent, this.getClass().getName());
	}
	abstract void handlePermissionCheck(PermissionRequestIntent intent);
}

abstract class FinalPermissionHandler extends PermissionHandler {
	protected final static int GRANT = 1;
	protected final static int DENY = 0;
	protected final static int KILL = -1;

	protected boolean remember = false;
	public FinalPermissionHandler setRemembered(boolean r)
	{
		remember = r;
		return this;
	}
	protected void sendReply(PermissionRequestIntent intent, int arg0)
	{
	   Message msg = Message.obtain();
	   msg.what = intent.getResponseToken();
	   msg.arg1 = arg0;
//	   msg.arg2 = 0; //Used as "remember-decision", not important here
	   try {
		   intent.getResultMessenger().send(msg);
	   } catch (RemoteException e) {
		   e.printStackTrace();
	   } finally {
		   msg.recycle();
	   }
	}
}
class GrantPermissionHandler extends FinalPermissionHandler {

	@Override
	public void handlePermissionCheck(PermissionRequestIntent intent) {
		if (remember)updatePolicyStorage(intent);
		sendReply(intent, GRANT);
	}

}
class DenyPermissionHandler extends FinalPermissionHandler {

	@Override
	public void handlePermissionCheck(PermissionRequestIntent intent) {
		if (remember)updatePolicyStorage(intent);
		sendReply(intent, DENY);
	}

}

class KillAppPermissionHandler extends FinalPermissionHandler {
	public void handlePermissionCheck(PermissionRequestIntent intent) {
		if (remember)updatePolicyStorage(intent);
		sendReply(intent, KILL);
	}
	
}

class UserConsentHandler extends PermissionHandler {
//	public UserConsentHandler(PermissionHandler first, PermissionHandler second, PermissionHandler third) {
//		
//	}
	@Override
	public void handlePermissionCheck(PermissionRequestIntent intent) {
		updatePolicyStorage(intent);
		intent.setComponent(new ComponentName(context, UserNotificationActivity.class));
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//		intent.putExtra("handlers", new Parcelable[]{new GrantPermissionHandler(), new DenyPermissionHandler(), new KillAppPermissionHandler() })
		context.startActivity(intent);
	}
	
}

