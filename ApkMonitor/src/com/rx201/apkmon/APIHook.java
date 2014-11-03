package com.rx201.apkmon;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.Application;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.rx201.apkmon.permissions.AurasiumPermission;
import com.rx201.apkmon.permissions.ExecPermission;
import com.rx201.apkmon.permissions.NativeLibraryPermission;
import com.rx201.apkmon.permissions.PermissionRequestIntent;
import com.rx201.apkmon.permissions.PrivacyPermission;
import com.rx201.apkmon.permissions.SMSPermission;
import com.rx201.apkmon.permissions.SocketConnectPermission;

class TransactionInfo {
	public String Descriptor;
	public int Code;
	public TransactionHandler Handler;
	public TransactionInfo(String descriptor, int code, TransactionHandler handler)
	{
		Descriptor = descriptor;
		Code = code;
		Handler = handler;
	}
}

class TransactionHandlerFactory {
	/* Helper function to convert a code to its string representation as in its defining class */
    private static Map<String, Map<Integer, String>> TranslationCache = new HashMap<String, Map<Integer, String>>();
	synchronized static String TranslateCode(String descriptor, int Code)
    {
    	if (!TranslationCache.containsKey(descriptor))
    	{
    		Map<Integer, String> m = new HashMap<Integer, String>();
			TranslationCache.put(descriptor, m);
	    	try {
	    		Class cls = null;
	    		try{
	    			cls = Class.forName(descriptor + "$Stub");
	    		}catch(ClassNotFoundException e){}
				if (cls == null)
				{
		    		try{
		    			cls = Class.forName(descriptor);
		    		}catch(ClassNotFoundException e){}
				}
				if (cls != null)
				{
					Field[] fields = cls.getDeclaredFields();
					for(int i=0;i < fields.length; i++)
					{
						if (fields[i].getGenericType().equals(Integer.TYPE))
						{
							fields[i].setAccessible(true);
							m.put(fields[i].getInt(null), fields[i].getName());
						}
					}
				}
			} catch (Exception e) {
			}
    	}
		Map<Integer, String> m = TranslationCache.get(descriptor);
		if (m.size() == 0)
			return "*UnknownCls " + Code;
		else
		{
			if (m.containsKey(Code))
				return m.get(Code);
			else
				return "UnknownCode " + Code;
		}
    }
    
	public static TransactionHandler getHandler(String Descriptor, int Code) {
		String TransactionName = TranslateCode(Descriptor, Code);

		if (Descriptor.equals("com.android.internal.telephony.ISms"))
		{
    		if (TransactionName.equals("TRANSACTION_sendText"))
    			return new SMSSendTextHandler();
			
		}
		else if (Descriptor.equals("com.android.internal.telephony.IPhoneSubInfo"))
		{
			if (TransactionName.equals("TRANSACTION_getLine1Number"))
				return new GetLineNumberHandler();
			else if (TransactionName.equals("TRANSACTION_getDeviceId"))
				return new GetDeviceIDHandler();
			else if (TransactionName.equals("TRANSACTION_getSubscriberId"))
				return new GetSubscriberIDHander();
		}
		else if (Descriptor.equals("android.app.IApplicationThread"))
		{
			if (TransactionName.equals("SCHEDULE_RECEIVER_TRANSACTION"))
				return new BroadcastReceiverHandler();
		}
		else if (Descriptor.equals("android.content.IContentProvider"))
		{
			if (TransactionName.equals("QUERY_TRANSACTION"))
				return new QueryContentProviderHandler();
		}
		else if (Descriptor.equals("android.location.ILocationManager"))
		{
			if (TransactionName.equals("TRANSACTION_getLastKnownLocation"))
				return new LocationGetLastKnownHandler();
			else if (TransactionName.equals("TRANSACTION_requestLocationUpdates"))
				return new LocationRequestUpdatesHandler();
			else if (TransactionName.equals("TRANSACTION_requestLocationUpdatesPI"))
				return new LocationRequestUpdatesHandler();
			else if (TransactionName.equals("TRANSACTION_addProximityAlert"))
				return new LocationAddProximityAlertHandler();
		}
		return null;
	}
}

class TransactionHandler {
    /*
     *  Security checks before the transaction happens, useful to modify its arguments
     *  Note that the first interface token is not included in the parcel and is handled separately by 
     *  underlying native code.
     *  Return values:
     *  0   -   Nothing is changed, the API hooking trampoline should proceed normally.
     *  byte[] -   Marshalled modified Parcel should be injected back to the ioctl command buffer.
     */
    public byte[] HandleBeforeTransact(String Descriptor, Parcel parcel){
    	return null;
    }
    
    /*
     *  Security checks after the transaction happens, useful to modify its results
     *  Return values:
     *  NULL   -   Nothing is changed, the API hooking trampoline should proceed normally.
     *  byte[] -   Marshalled modified Parcel should be injected back to the ioctl reply buffer.
     */
    public byte[] HandleAfterTransact(Parcel parcel){
    	return null;
    }
}
class SMSSendTextHandler extends TransactionHandler {
	private static boolean mHasPackageArgument;
	
	static {
		mHasPackageArgument = false;
		try {
			Class iSmsCls = Class.forName("com.android.internal.telephony.ISms");
			iSmsCls.getMethod("sendText",new Class[] {
					String.class, String.class, String.class, String.class, PendingIntent.class, PendingIntent.class});
			mHasPackageArgument = true;
		} catch (NoSuchMethodException e) {
		} catch (ClassNotFoundException e) {
		}
	}
	private boolean intercepted = false;
	@Override
	public byte[] HandleBeforeTransact(String Descriptor, Parcel parcel) {
		String callingPkg = null;
		if (mHasPackageArgument)
			callingPkg = parcel.readString();
		final String destAddr = parcel.readString();
		final String scAddr = parcel.readString();
		final String text = parcel.readString();
		APIHook.LOG_I("HandleBeforeTransact", String.format("SendText: dest:%s, src:%s, text:%s", destAddr, scAddr, text));
		if (!Utility.PolicyCheck(new SMSPermission(destAddr, text)))
		{
			intercepted = true;
	    	// Try to modify the sendText parameters.
	    	Parcel newparcel = Parcel.obtain();
			if (mHasPackageArgument)
				newparcel.writeString(callingPkg);
	    	newparcel.writeString("");//destAddr
	    	newparcel.writeString(scAddr);
	    	newparcel.writeString("BLOCKED:" + text);
	    	// sentIntent == null
    		newparcel.writeInt(0);
	    	// deliveryIntent == null
    		newparcel.writeInt(0);

    		byte[] result = newparcel.marshall();
	    	newparcel.recycle();
	    	
	    	return result;
		}
		return null;
	}
	@Override
	public byte[] HandleAfterTransact(Parcel parcel) {
		if (intercepted)
		{
			try {
				parcel.readException();
			}catch(NullPointerException e)  
			{
				// This is very likely to be caused by our intercepting attempt (Replacing destAddr to ""),
				// And we don't want the resultant exception to be visible by the upper level application.
				Parcel p = Parcel.obtain();
				p.writeNoException();
				byte[] result = p.marshall();
				p.recycle();
				return result;
			}
		}
		return null;
	}
}

class BroadcastReceiverHandler extends TransactionHandler {

	@Override
	public byte[] HandleBeforeTransact(String Descriptor,
			Parcel parcel) {
        Intent intent = Intent.CREATOR.createFromParcel(parcel);
        ActivityInfo info = ActivityInfo.CREATOR.createFromParcel(parcel);
        int resultCode = parcel.readInt();
        String resultData = parcel.readString();
        Bundle resultExtras = parcel.readBundle();
        boolean sync = parcel.readInt() != 0;
        
        String IntentAction = intent.getAction();
        String IntentReceiver = intent.getComponent().getClassName();
        APIHook.LOG_I("HandleBeforeTransact", String.format("Broadcast Receiver %s %s will be called", IntentAction, IntentReceiver));
        return null;
	}
}

class GetLineNumberHandler extends TransactionHandler {

	@Override
	public byte[] HandleAfterTransact(Parcel parcel) {
		if (!Utility.PolicyCheck(new PrivacyPermission(PrivacyPermission.PRIVACY_TYPE.PhoneNumber)))
		{
			try{
				parcel.readException();
			} catch(Exception e)
			{
				return null;
			}
	        Parcel newp = Parcel.obtain();
	        newp.writeNoException();
	        newp.writeString("07712345678");
	        return newp.marshall();
		}
		return null;
	}
}

class GetDeviceIDHandler extends TransactionHandler {
	@Override
	public byte[] HandleAfterTransact(Parcel parcel) {
		if (!Utility.PolicyCheck(new PrivacyPermission(PrivacyPermission.PRIVACY_TYPE.IMEI)))
		{
			try{
				parcel.readException();
			} catch(Exception e)
			{
				return null;
			}
	        Parcel newp = Parcel.obtain();
	        newp.writeNoException();
	        newp.writeString("000000000000000");
	        return newp.marshall();
		}
		return null;
	}
}
class GetSubscriberIDHander extends TransactionHandler {
	@Override
	public byte[] HandleAfterTransact(Parcel parcel) {
		if (!Utility.PolicyCheck(new PrivacyPermission(PrivacyPermission.PRIVACY_TYPE.IMSI)))
		{
			try{
				parcel.readException();
			} catch(Exception e)
			{
				return null;
			}
	        Parcel newp = Parcel.obtain();
	        newp.writeNoException();
	        newp.writeString("310260000000000");
	        return newp.marshall();
		}
		return null;
	}
}

class QueryContentProviderHandler extends TransactionHandler {
	private static final String ContactURL = ContactsContract.Contacts.CONTENT_URI.toString();
	private String callingPkg = null;
	private boolean intercept = false;
	@Override
	public byte[] HandleBeforeTransact(String Descriptor, Parcel parcel) {
	    if (android.os.Build.VERSION.SDK_INT >= 18) {
	        callingPkg = parcel.readString();
	    }
		Uri url = Uri.CREATOR.createFromParcel(parcel);
		String url_string = url.toString();
		APIHook.LOG_I("APIHook", "Query Content Provider: " + url_string);
		if (url_string.startsWith(ContactURL))
			if (!Utility.PolicyCheck(new PrivacyPermission(PrivacyPermission.PRIVACY_TYPE.Contact)))
				intercept = true;
		return super.HandleBeforeTransact(Descriptor, parcel);
	}

	@Override
	public byte[] HandleAfterTransact(Parcel parcel) {
		if (intercept)
		{
			Parcel p = Parcel.obtain();
			p.writeNoException();
	        if (android.os.Build.VERSION.SDK_INT >= 18) {
	            p.writeString(callingPkg);
	        }
			// Return a null bulkCursorBinder object.
			p.writeStrongBinder(null);
			byte[] result = p.marshall();
			p.recycle();
			return result;
		}
		else
		{
			return super.HandleAfterTransact(parcel);
		}
	}
}

class LocationGetLastKnownHandler extends TransactionHandler {

	private boolean intercept = false;
	@Override
	public byte[] HandleBeforeTransact(String Descriptor, Parcel parcel) {
		if (!Utility.PolicyCheck(new PrivacyPermission(PrivacyPermission.PRIVACY_TYPE.GPS_Last)))
			intercept = true;
		else
			intercept = false;
		return null;
	}
	@Override
	public byte[] HandleAfterTransact(Parcel parcel) {
		if (intercept)
		{
			Parcel p = Parcel.obtain();
			p.writeNoException();
			p.writeInt(0); // Returns location = null
			byte[] result = p.marshall();
			p.recycle();
			return result;
		}
		return null;
	}
}

class LocationAddProximityAlertHandler extends TransactionHandler {
	@Override
	public byte[] HandleBeforeTransact(String Descriptor, Parcel parcel) {
		if (!Utility.PolicyCheck(new PrivacyPermission(PrivacyPermission.PRIVACY_TYPE.GPS_Proximity)))
		{
			double latitude = parcel.readDouble();
			double longitude = parcel.readDouble();
			float radius = parcel.readFloat();
			long expiration = parcel.readLong();
			
			PendingIntent intent = null;
			if ((0 != parcel.readInt())) {
				intent = PendingIntent.CREATOR.createFromParcel(parcel);
			}
			
			Parcel p = Parcel.obtain();
			p.writeDouble(latitude);
			p.writeDouble(longitude);
			p.writeFloat(radius);
			p.writeLong(0); // Set expiration to zero to render the request useless
			if (intent != null)
			{
				p.writeInt(1);
				intent.writeToParcel(p, 0);
			}
			else
				p.writeInt(0);
			byte[] result = p.marshall();
			p.recycle();
			return result;
		}
		return null;
	}
}

class LocationRequestUpdatesHandler extends TransactionHandler {

	private boolean intercept = false;
	@Override
	public byte[] HandleBeforeTransact(String Descriptor, Parcel data) {
		if (!Utility.PolicyCheck(new PrivacyPermission(PrivacyPermission.PRIVACY_TYPE.GPS_Last)))
		{
			intercept = true;
	        
			Parcel p = Parcel.obtain();

			String provider = data.readString();
			p.writeString(provider);
			
			Criteria criteria = null;
			if ( 0 != data.readInt() ) {
				criteria = android.location.Criteria.CREATOR.createFromParcel(data);
				p.writeInt(1);
				criteria.writeToParcel(p, 0);
			}
			else
			{
				p.writeInt(0);
			}
			
			long minTime = data.readLong();
			p.writeLong(minTime);
			
			float minDistance = data.readFloat();
			p.writeFloat(minDistance);
			
			boolean singleShot = (0 != data.readInt());
			p.writeInt(singleShot ? 1: 0);
			
//			_arg5 = android.location.ILocationListener.Stub.asInterface(data.readStrongBinder());
//			_arg5 = android.app.PendingIntent.CREATOR.createFromParcel(data);
			p.writeInt(0); // Set LocationListener/PendingIntent to null
			byte[] result = p.marshall();
			p.recycle();
			return result;			
		}
		else
			intercept = false;
		return null;
	}
	@Override
	public byte[] HandleAfterTransact(Parcel parcel) {
		if (intercept)
		{
			// Inhibit the server side null pointer exception as we just passed invalid argument to it. 
			try{
				parcel.readException();
			}catch (Exception e) 
			{
				Parcel p = Parcel.obtain();
				p.writeNoException();
				byte[] result = p.marshall();
				p.recycle();
				return result;
			}
		}
		return null;
	}
}

public class APIHook extends Application {
	static final boolean DEBUG = false;
    public native String  WriteTest();
    
    public static native void KillMe();
    public static native void Hook(boolean HasStrictMode);
    
    public static native void saveDecisions(String path, byte[] data);
    public static native byte[] loadDecisions(String path);
    static {
        System.loadLibrary("apihook");
        /* android.os.Build.VERSION_CODES.GINGERBREAD == 0x9, when StrictMode is introduced */
        Hook(android.os.Build.VERSION.SDK_INT >= 9);
    }

    public APIHook()
    {
    	super();
    	APIHook.app = this;
    }
    
    public static APIHook app;
    
    public static boolean StartLogged = false;
    public static Context getSystemContext() {
    	//TODO: potential NULL ptr bug here.
    	return app.getBaseContext();
    }

    public static void LOG_I(String tag, String msg) {
    	if (DEBUG)
    		Log.i(tag, msg);
    }
    public static void LOG_E(String tag, String msg) {
		Log.e(tag, msg);
    }
    private static Map<Integer, Stack<TransactionInfo>> PendingLocalTransactions = new HashMap<Integer, Stack<TransactionInfo>>();
    private static Map<Integer, Stack<TransactionInfo>> PendingRemoteTransactions = new HashMap<Integer, Stack<TransactionInfo>>();
    
    private static Stack<TransactionInfo> cached_ti = new Stack<TransactionInfo>();
    private static TransactionInfo obtainTI()
    {
    	if (cached_ti.size() > 0)
    	{
    		TransactionInfo t = cached_ti.pop();
    		return t;
    	}
    	else
    		return null;
    }
    private static void recycleTI(TransactionInfo ti)
    {
    	cached_ti.push(ti);
    }
    synchronized private static Stack<TransactionInfo> getTransactionStack(boolean isRemote, int ThreadID)
    {
    	Map<Integer, Stack<TransactionInfo>> stackmap = isRemote ? PendingRemoteTransactions :  PendingLocalTransactions;
    	Stack<TransactionInfo> stack = stackmap.get(ThreadID);
    	if (stack == null)
    	{
    		stack = new Stack<TransactionInfo>();
    		stackmap.put(ThreadID, stack);
    	}
    	return stack;
    }
    private static byte[] ProcessTransaction(Boolean isRemote, int ThreadID, String Descriptor, int transactionCode, Parcel data)
    {
//    	String TransactionName = TransactionHandlerFactory.TranslateCode(Descriptor, transactionCode);
//    	APIHook.LOG_I("apihook", String.format("%d %s %s %s", ThreadID, isRemote ? "on_BC_TRANSACTION" : "on_BR_TRANSACTION",
//    													Descriptor, TransactionName));
    	TransactionHandler handler = TransactionHandlerFactory.getHandler(Descriptor, transactionCode);
    	TransactionInfo tr = obtainTI();
    	if (tr == null)
    		tr = new TransactionInfo(Descriptor, transactionCode, handler);
    	else
    	{
    		tr.Descriptor = Descriptor;
    		tr.Code = transactionCode;
    		tr.Handler = handler;
    	}
    	getTransactionStack(isRemote, ThreadID).push(tr);
    	if (handler != null)
    		return handler.HandleBeforeTransact(Descriptor, data);
    	else
    		return null;
    }
    private static byte[] ProcessReply(Boolean isRemote, int ThreadID, Parcel data)
    {
    	Stack<TransactionInfo> stack = getTransactionStack(isRemote, ThreadID);
    	if (stack.size() == 0)
    	{
    		APIHook.LOG_E("apihook", String.format("%d ProcessReply: Empty stack, skip.", ThreadID));
        	return null;
    	}
    	TransactionInfo tr = getTransactionStack(isRemote, ThreadID).pop();
//    	APIHook.LOG_I("apihook", String.format("%d %s %s", ThreadID, isRemote ? "on_BR_REPLY" : "on_BC_REPLY", tr.Descriptor));
    	if (tr.Handler != null)
    	{
    		byte[] r =  tr.Handler.HandleAfterTransact(data);
    		recycleTI(tr);
    		return r;
    	}
    	else
    		return null;
    }
    /*
     *  Callback function for Bx_TRANSACTION/REPLY, take care when dealing with remote/local objects in the parcel
     *  Return values:
     *  0   -   Nothing is changed, the API hooking trampoline should proceed normally.
     *  byte[] -   Marshaled modified Parcel should be injected back to the ioctl command buffer.
     *  
     *  BC_TRANSACTION/BR_REPLY pair indicates a remote call and its return value, 
     *  while BR_TRANSACTION/BC_REPLY pair indicates a request to call local method and command to send the call's results to remote side.
     */
    //TODO: Remove redundant command argument
    public static byte[] on_BC_TRANSACTION(int ThreadID, int command, int transactionCode, String Descriptor, Parcel parcel)
    {
    	if (!StartLogged)
    	{
        	Utility.LogAccess("*****AppStart*****", null);
        	StartLogged = true;
    	}
    	return ProcessTransaction(true, ThreadID, Descriptor, transactionCode, parcel);
    }

    public static byte[] on_BR_TRANSACTION(int ThreadID, int command, int transactionCode, String Descriptor, Parcel parcel)
    {
    	return ProcessTransaction(false, ThreadID, Descriptor, transactionCode, parcel);
    }
    
    public static byte[] on_BC_REPLY(int ThreadID, int command, int transactionCode, Parcel parcel)
    {
    	return ProcessReply(false, ThreadID, parcel);
    }

    public static byte[] on_BR_REPLY(int ThreadID, int command, int transactionCode, Parcel parcel)
    {
    	return ProcessReply(true, ThreadID, parcel);
    }
    
    private static HashMap<String, String> ReverseDNS = new HashMap<String, String>();
    // Callback function
    public static int onBeforeConnect(int sockfd, String addr, int port)
    {       
    	String RemoteIP = (addr.startsWith("::ffff:") ? addr.substring(7) : addr); // IPv4-mapped address.

    	String DomainName = ReverseDNS.get(addr);
    	if (DomainName == null && addr.startsWith("::ffff:")) 
    		DomainName = ReverseDNS.get(addr.substring(7));
    	
    	boolean result =  Utility.PolicyCheck(new SocketConnectPermission(RemoteIP, DomainName, port));
    	return result ? 1 : 0;
    }
    // Callback function
    public static int onDNSResolve(String DomainName, String IPaddr)
    {       
    	// Sometimes this function gets called with DomainName(in ip form) == addr
    	if (!DomainName.equals(IPaddr))
    		ReverseDNS.put(IPaddr, DomainName);
    	return 0;
    }
    // Callback function
    public static int onDlOpen(String filename, int flag)
    {       
    	if (filename == null)
    		return 1;
    	if ((filename.startsWith("/system/lib/") || filename.startsWith("/vendor/lib/")) && (!filename.contains("..")))
    		return 1;
    	
		return Utility.PolicyCheck(new NativeLibraryPermission(filename)) ? 1 : 0;
    }
    // Callback function; returns 0 for disallow execvp.
    public static int onBeforeExecvp(String filename) {
		return Utility.PolicyCheck(new ExecPermission(filename)) ? 1 : 0;
    }
    
    
}
class Utility {
	static final String AppPackageName = APIHook.getSystemContext().getPackageName();
    static final String ConsentDialogActivityName = APIHookDialogActivity.class.getName();
	static final ComponentName LocalConsentDialogName = new ComponentName(AppPackageName, ConsentDialogActivityName);
	static final String SecurityManagerPackage = "com.rx201.asm";
	static final String SecurittManagerService = "com.rx201.asm.PermissionCheck";
	static final Intent SecurityManagerIntent = new Intent().setClassName(
			SecurityManagerPackage, SecurittManagerService);

	private static boolean isASMAvailable() {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<Boolean> result = executor.submit(new Callable<Boolean>() {
			public Boolean call() throws Exception {
//				APIHook.getSystemContext().startService(null);
//				APIHook.getSystemContext().getPackageManager().resolveService(null, 0);
				return APIHook
				.getSystemContext()
				.getPackageManager()
				.resolveService(SecurityManagerIntent,
						PackageManager.MATCH_DEFAULT_ONLY) != null;
			}
		});
		try {
			return result.get().booleanValue();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return false;
	}
  
	public static String now(String dateFormat) {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
		return sdf.format(cal.getTime());

	}

    public static synchronized void LogAccess(String PolicyName, String PolicyUID){
    	if (!APIHook.DEBUG) return;
    	try {
    	    BufferedWriter out = new BufferedWriter(new FileWriter("/sdcard/access.txt", true));
    	    out.write(String.format("%s %s: %s %s\n", 
    	    		now("yyyy-MM-dd hh:mm:ss"),
    	    		APIHook.getSystemContext().getPackageName(),
    	    		PolicyName, PolicyUID != null ? PolicyUID : ""));
    	    out.close();
    	} catch (Exception e) {
    	}
    }

    private static HashMap<String, Integer> SavedDecisions = new HashMap<String, Integer>();
    private static boolean DecisionsLoaded = false;
    // Retrieve saved policy decisions, 1 for allow, 0 for deny, -1 for kill app, -2 for non-existent.
    private static int LookupSavedPolicyDecision(String PolicyName, String PolicyUID)
    {
    	String key = PolicyName + PolicyUID;
    	Integer r;
    	synchronized(SavedDecisions) {
    		if (!DecisionsLoaded) {
    			byte[] data = APIHook.loadDecisions(APIHook.getSystemContext().getFilesDir().getAbsolutePath());
    			if (data != null)
    			{
					ByteArrayInputStream in = new ByteArrayInputStream(data);
					try {
						ObjectInputStream objIn = new ObjectInputStream(in);
						SavedDecisions.clear();
						SavedDecisions.putAll((HashMap<String, Integer>)objIn.readObject());
						objIn.close();
						in.close();
					} catch (IOException e) {
						APIHook.LOG_E("apkmon", Log.getStackTraceString(e));
					} catch (ClassNotFoundException e) {
						APIHook.LOG_E("apkmon", Log.getStackTraceString(e));
					}
    			}
    			DecisionsLoaded = true;
    		}
    		r = SavedDecisions.get(key);
    	}
    	if (r == null)
    		return -2;
    	else 
    		return r;
    }
    
    private static void SavePolicyDecision(String PolicyName, String PolicyUID,
			int decision) {
    	// Do not allow "kill app" to be remembered, as a usability issue.
    	if (decision == -1)
    		return;
    	String key = PolicyName + PolicyUID;
    	synchronized(SavedDecisions) {
    		SavedDecisions.put(key, decision);
    		
    		// Save results to persistent storage.
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				ObjectOutputStream objOut = new ObjectOutputStream(out);
    	        objOut.writeObject(SavedDecisions);
    	        objOut.close();
    	        APIHook.saveDecisions(APIHook.getSystemContext().getFilesDir().getAbsolutePath(), out.toByteArray());
    			out.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
	}
    
    
    /* Perform a policy check of the given operation. 
     * Delegates to ASM if it is installed on the device. Otherwise use local logic:
     * Check for saved/remembered decision first, then ask for user consent.
     * returns: true for allow, false for deny.
     */
	public static synchronized boolean PolicyCheck(AurasiumPermission permission) {
		int decision = 0;
		if (isASMAvailable()) {
			RemotePermissionChecker checker = new RemotePermissionChecker(
					permission, SecurityManagerIntent.getComponent(), RemotePermissionChecker.RemoteType.Service);
			decision = checker.check();
		} else {
			String permissionName = permission.getPermissionIdentifier();
			String permissionUID = permission.getGroupingIdentifier();
			LogAccess(permissionName, permissionUID);

			decision = LookupSavedPolicyDecision(permissionName, permissionUID);
			if (decision == -2) // Non-existent in local policy storage
			{
				RemotePermissionChecker checker = new RemotePermissionChecker(
						permission, LocalConsentDialogName, RemotePermissionChecker.RemoteType.Activity);
//				if (APIHook.isTesting())
//					checker.SetDefaultChoice(3, 1);

				decision = checker.check();
				// Save decision under user request.
				if (checker.getExtraResult() != 0)
					SavePolicyDecision(permissionName, permissionUID, decision);
			}
		}
		// User requests to close this application.
		if (decision == -1) {
			APIHook.LOG_I(APIHook.getSystemContext().getPackageName(),
					"Application termination at user's request.");
			APIHook.KillMe();
		}
		return decision == 1;
	}
    
	private static Class clsAMN = null;
    private static Method AM_getDefault;
    private static Class IfAM;
    private static Class cls_IApplicationThread;
    @SuppressWarnings("unchecked")
	private static void resolveClasses() throws ClassNotFoundException, SecurityException, NoSuchMethodException 
    {
		// Have we initialised all the reflected classes?
		if (clsAMN == null)
		{
			clsAMN = Class.forName("android.app.ActivityManagerNative");
			AM_getDefault = clsAMN.getMethod("getDefault", (Class[])null);

			IfAM = Class.forName("android.app.IActivityManager");
			cls_IApplicationThread = Class.forName("android.app.IApplicationThread");
		}
    }    	
    private static Method AM_startActivity;
    private static Method AM_startService;
    private static Class pfd;
	@SuppressWarnings("unchecked")
	public static boolean LowlevelStartActivity(Intent intent)
    {
    	try {
    		resolveClasses();
    		if (AM_startActivity == null)
    		{
				// Prior to ICS, the interfaces are different.
				if (android.os.Build.VERSION.SDK_INT < 14)
					AM_startActivity = IfAM.getMethod("startActivity", new Class[] {cls_IApplicationThread,
				            Intent.class, String.class, Uri[].class, int.class, IBinder.class, String.class, int.class, boolean.class, boolean.class});
				else if (android.os.Build.VERSION.SDK_INT < 16) // This is the signature for ICS
				{
					pfd = Class.forName("android.os.ParcelFileDescriptor");
					AM_startActivity = IfAM.getMethod("startActivity", new Class[] {cls_IApplicationThread,
				            Intent.class, String.class, Uri[].class, int.class, IBinder.class, String.class, int.class, boolean.class, boolean.class,
				            String.class, pfd, boolean.class});
				} else if (android.os.Build.VERSION.SDK_INT < 18) {  // Signature for Jellybean 4.1, 4.2
					pfd = Class.forName("android.os.ParcelFileDescriptor");
					AM_startActivity = IfAM.getMethod("startActivity", new Class[] {cls_IApplicationThread,
				            Intent.class, String.class, IBinder.class, String.class, int.class, int.class, String.class,
				            pfd, Bundle.class});
				} else { // Jellybean 4.3 or later
					pfd = Class.forName("android.os.ParcelFileDescriptor");
					AM_startActivity = IfAM.getMethod("startActivity", new Class[] {cls_IApplicationThread,
				            String.class, Intent.class, String.class, IBinder.class, String.class, int.class, int.class, String.class,
				            pfd, Bundle.class});
					
				}
    		}
		    Object mAm = AM_getDefault.invoke(null, (Object[])null);
			if (android.os.Build.VERSION.SDK_INT < 14)
				AM_startActivity.invoke(mAm, null, intent, intent.getType(), null, 0, null, null, 0, false, false);
			if (android.os.Build.VERSION.SDK_INT < 16)
				AM_startActivity.invoke(mAm, null, intent, intent.getType(), null, 0, null, null, 0, false, false, null, null, false);
			if (android.os.Build.VERSION.SDK_INT < 18)
				AM_startActivity.invoke(mAm, intent, intent.getType(), null, null, 0, 0, null, null, null);
			else
				AM_startActivity.invoke(mAm, null, null, intent, intent.getType(), null, null, 0, 0, null, null, null);
				
	    	return true;
		} catch (Exception e) {
			APIHook.LOG_E("apkmon", "exception: " + e.getMessage());
			APIHook.LOG_E("apkmon", Log.getStackTraceString(e));
	    	return false;
		}
    }
    
	@SuppressWarnings("unchecked")
	public static boolean LowlevelStartService(Intent intent)
    {
    	try {
    		resolveClasses();
    		
    		if (AM_startService == null)
    		{
    			AM_startService = IfAM.getMethod("startService", new Class[] {cls_IApplicationThread, Intent.class, String.class});
    		}
		    Object mAm = AM_getDefault.invoke(null, (Object[])null);
		    AM_startService.invoke(mAm, null, intent, null);
			
	    	return true;
		} catch (Exception e) {
			APIHook.LOG_E("apkmon", "exception: " + e.getMessage());
			APIHook.LOG_E("apkmon", Log.getStackTraceString(e));
	    	return false;
		}
    }
}

class RemotePermissionChecker implements Runnable {
	// 1: allow; 0: deny; -1: Exit.
	private int Result = -1;

	public int getResult() {
		return Result;
	}

	private int extraResult;

	public int getExtraResult() {
		return extraResult;
	}

	private int DefaultDelay = 0;
	private int DefaultChoice;

	public void SetDefaultChoice(int delay, int choice) {
		DefaultChoice = choice;
		DefaultDelay = delay;
	}

	private static Random prng = new Random();
	private int responseToken;
	private AurasiumPermission permission;
	private ComponentName target;
	public enum RemoteType {Service, Activity};
	private RemoteType targetType;
	public RemotePermissionChecker(AurasiumPermission permission,
			ComponentName target,
			RemoteType type) {
		this.permission = permission;
		this.target = target;
		this.targetType = type;
		responseToken = prng.nextInt();
	}

	@SuppressWarnings("unused")
	private RemotePermissionChecker() {
	}

	@Override
	public void run() {
		Looper.prepare();
		PermissionRequestIntent i = new PermissionRequestIntent();
		i.setAction("ACTION_SHOW_DIALOG");

		i.setComponent(target);
		i.setApplicationPackage(Utility.AppPackageName);
		i.setResponseToken(responseToken);
		i.setPermissionRequest(permission);
		i.setResultMessenger(new Messenger(new Handler() {
			@Override
			// Handler(Callback) when the remote dialog activity invokes
			// Messenger.send(msg)
			public void handleMessage(Message msg) {
				if (msg.what == responseToken) {
					Looper.myLooper().quit();
					Result = msg.arg1;
					extraResult = msg.arg2;
				} else
					super.handleMessage(msg);
			}
		}));
		if (DefaultDelay > 0) {
			i.setDefaultDelay(DefaultDelay);
			i.setDefaultChoice(DefaultChoice);
		}
		boolean succeed;
		switch (targetType) {
		case Service:
			succeed = Utility.LowlevelStartService(i);
			break;
		case Activity:
			succeed = Utility.LowlevelStartActivity(i);
			break;
		default:
			succeed = false;
		}
		if (succeed) {
			Looper.loop();
		}
	}

	public int check() {
		Thread MsgboxThread = new Thread(this);
		MsgboxThread.start();
		while (MsgboxThread.isAlive()) {
			try {
				MsgboxThread.join();
			} catch (InterruptedException e) {
				APIHook.LOG_E("RemotePermission", "Current thread interrupted.");
			}
		}
		return getResult();
	}
}
