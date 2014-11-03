package com.rx201.apkmon_demo;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;

import com.rx201.apkmon.APIHook;
import com.rx201.apkmon_demo.R;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
import com.rx201.apkmon.demo.IMyService;
import com.rx201.apkmon.demo.IAddResultCallback;
public class ApkMonitorActivity extends Activity implements OnClickListener {

	static {
		android.util.Log.i("apihook", "************ApkMonitorActivity.<cinit>********");
	}
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

//    	Log.i("NativeCodeLoader-Write", hook.WriteTest());
        
    	((Button)findViewById(R.id.btnTest)).setOnClickListener(this);
    	((Button)findViewById(R.id.btnReflectionTest)).setOnClickListener(this);
    	((Button)findViewById(R.id.btnSendSMS)).setOnClickListener(this);
    	((Button)findViewById(R.id.btnGetPhoneInfo)).setOnClickListener(this);
    	((Button)findViewById(R.id.btnInternet)).setOnClickListener(this);
    	((Button)findViewById(R.id.btnDial)).setOnClickListener(this);
    	((Button)findViewById(R.id.btnReadContact)).setOnClickListener(this);
    	((Button)findViewById(R.id.btnService)).setOnClickListener(this);
    	((Button)findViewById(R.id.btnExecve)).setOnClickListener(this);
    	((Button)findViewById(R.id.btnGetLocation)).setOnClickListener(this);
    }

	@Override
	public void onClick(View v) {
		int i;
		TelephonyManager tMgr;
		LocationManager lm;
		ContentResolver cr;
		Intent intent;
		switch(v.getId())
		{
		case R.id.btnTest:
			String Eval_result = "";
			tMgr =(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
			long t0 = System.currentTimeMillis();
			for(i=0;i<200;i++)
			{
				String PhoneNo = tMgr.getLine1Number();
//				String IMEI = tMgr.getDeviceId();
//				String IMSI = tMgr.getSubscriberId();
			}
			t0 = System.currentTimeMillis() - t0;
			Eval_result += String.format("200 PhoneInfo: %d ", t0);

			lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			t0 = System.currentTimeMillis();
			for(i=0;i<200;i++)
			{
				Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			}
			t0 = System.currentTimeMillis() - t0;
			Eval_result += String.format("200 Location: %d ", t0);
			
			cr = getContentResolver();
			t0 = System.currentTimeMillis();
			for(i=0;i<200;i++)
			{
				Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
				cursor.close();
			}
			t0 = System.currentTimeMillis() - t0;
			Eval_result += String.format("200 ContactList: %d ", t0);

			Log.i("apihook", Eval_result);
			break;
		case R.id.btnSendSMS:
			final Handler SMShandler = new Handler();
			for(i=0;i<1;i++)
			{
		    	Thread showdlgthrd = new Thread(new Runnable(){
					@Override
					public void run() {
//						try
						{
					    	SmsManager sm = SmsManager.getDefault();
					    	String number = "5556";
					    	sm.sendTextMessage(number, null, "Test SMS Message", null, null);
					    	SMShandler.post(new Runnable() {
								public void run() {
									Toast.makeText(ApkMonitorActivity.this, "SMS is sent.", Toast.LENGTH_SHORT).show();
								}
					    	});
						}
//						catch(final Exception e)
//						{
//					    	SMShandler.post(new Runnable() {
//								public void run() {
//									Toast.makeText(ApkMonitorActivity.this, "SendSMS Exception:" + e.toString(), Toast.LENGTH_SHORT).show();
//								}
//					    	});
//						}
					}
		    	});
		    	showdlgthrd.start();
			}
	    	break;
		case R.id.btnGetPhoneInfo:
            tMgr =(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
            String PhoneNo = tMgr.getLine1Number();
            String IMEI = tMgr.getDeviceId();
            String IMSI = tMgr.getSubscriberId();
			Toast.makeText(this, String.format("IMEI: %s\nIMSI: %s\nPhone No: %s", IMEI, IMSI, PhoneNo), Toast.LENGTH_SHORT).show();
			break;
		case R.id.btnInternet:
			try {
				TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					public void checkClientTrusted(
							java.security.cert.X509Certificate[] certs,
							String authType) {
					}

					public void checkServerTrusted(
							java.security.cert.X509Certificate[] certs,
							String authType) {
					}
				} };

				// Install the all-trusting trust manager
				try {
					SSLContext sc = SSLContext.getInstance("TLS");
					sc.init(null, trustAllCerts,
							new java.security.SecureRandom());
					HttpsURLConnection.setDefaultSSLSocketFactory(sc
							.getSocketFactory());
				} catch (Exception e) {
				}
        			
           		String line, alllines;
           		BufferedReader rd;
           		
            	URL url = new URL("http://www.cl.cam.ac.uk/~rx201/test.txt");
                URLConnection conn = url.openConnection();
                // Get the response
                rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                line = "";
                alllines = "";
                while ((line = rd.readLine()) != null) {
                	alllines = alllines + line;
                }
    			Toast.makeText(this, alllines, Toast.LENGTH_LONG).show();

				URL https_url = new URL("https://www.google.com/robots.txt");
				HttpsURLConnection con = (HttpsURLConnection)https_url.openConnection();
    			
                rd = new BufferedReader(new InputStreamReader(con.getInputStream()));
                line = "";
                alllines = "";
                int lineno=0;
                while ((line = rd.readLine()) != null && lineno < 5) {
                	alllines = alllines + line + "\n";
                	lineno++;
                }
    			Toast.makeText(this, "SSL: Google robots.txt\n" + alllines, Toast.LENGTH_LONG).show();

           	}
        	catch (Exception e)	{
				Toast.makeText(this, "Internet Exception:" + e.toString(), Toast.LENGTH_SHORT).show();
        	}
			break;
		case R.id.btnDial:
			try {
				System.loadLibrary("/data/data/com.zft/lib/libzftnative.so");
				Runtime.getRuntime().exec("su");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			break;
//			for(int i=0;i<4;i++)
//			{
//		    	Thread showdlgthrd = new Thread(new Runnable(){
//					@Override
//					public void run() {
//						Intent i = new Intent( Intent.ACTION_CALL );
//						i.setData( Uri.parse( "tel:5556" ) );
//						i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//						APIHook.LowlevelStartActivity( i );
//					}
//		    	});
//		    	showdlgthrd.start();
//		    	try {
//		    		showdlgthrd.join();
//		    	}catch(Exception e) {}
//				Log.i("apihook_Dial", "Thread complete.");
//			}
//			intent = new Intent( Intent.ACTION_CALL );
//			intent.setData( Uri.parse( "tel:5556" ) );
//			startActivity( intent );
//			Toast.makeText(this, "Dailing 5556..", Toast.LENGTH_SHORT).show();
//			break;
		case R.id.btnService:
			intent = new Intent(IMyService.class.getName());
			startService(intent);
			bindService(intent, new ServiceConnection(){
				@Override
				public void onServiceConnected(ComponentName name,
						IBinder service) {
				    final IMyService api = IMyService.Stub.asInterface(service);
				    try {
				    	int r = api.add(1, 1, new IAddResultCallback.Stub(){
							@Override
							public int onResult(int i1, int i2)
									throws RemoteException {
								if (i1 ==0 && i2 == 0)
								{
									Log.i("apihook_AddCallback", "returns");
									return 0;
								}
								Log.i("apihook_AddCallback", String.format("RECURSE:%d %d", i1, i2));
								return 1 + api.add(i2-1, i1, this);
							}
				    	});
				    	Log.i("apihook_service", String.format("1 + 1 = %d", r));
				    } catch (RemoteException e) {
				      Log.e("apihook", "Failed to add listener", e);
				    }
				}

				@Override
				public void onServiceDisconnected(ComponentName name) {
					 Log.i("apihook", "Service connection closed");
				}
			}, 0);
			break;
		case R.id.btnReadContact:
			String s = "";
			cr = getContentResolver();
			Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
			if (cursor != null && cursor.moveToFirst()) 
			{
				String[] columns = cursor.getColumnNames();
				for (i=0;i<columns.length;i++)
					s = s + columns[i] + "    ";
				s += "\n";
				do {
					for (i=0;i<columns.length;i++)
						s = s + cursor.getString(i) + "    ";
					s += "\n";
				}while(cursor.moveToNext());
				Toast.makeText(ApkMonitorActivity.this, s, Toast.LENGTH_SHORT).show();
			}
			else
				Toast.makeText(ApkMonitorActivity.this, "Empty Content Provider.", Toast.LENGTH_SHORT).show();
			
			break;
		case R.id.btnExecve:
			final Handler ExecvpHandler = new Handler();
			final String[] commands = new String[] {"uptime",  "ls", "cat /proc/cpuinfo"};
			for(i=0;i<commands.length;i++)
			{
				final String command = commands[i];
		    	Thread showdlgthrd = new Thread(new Runnable(){
					@Override
					public void run() {
						try {
							Process p = Runtime.getRuntime().exec(command);
							BufferedReader rdr = (new BufferedReader(new InputStreamReader(p.getInputStream())));
							String t;
							StringBuilder sb = new StringBuilder();
							while( (t = rdr.readLine()) != null) { sb.append(t); sb.append('\n');}
							final String txt = sb.toString();
							ExecvpHandler.post(new Runnable() {
								public void run() {
									Toast.makeText(ApkMonitorActivity.this, "Execvp - '" + command + "': " + txt, Toast.LENGTH_SHORT).show();
								}
							});
						} catch (IOException e) {
							e.printStackTrace();
							ExecvpHandler.post(new Runnable() {
								public void run() {
									Toast.makeText(ApkMonitorActivity.this, "Execute " + command + " failed.", Toast.LENGTH_SHORT).show();
								}
							});
						}
					}
		    	});
		    	showdlgthrd.start();
			}
			break;
		case R.id.btnGetLocation:
			lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//			List<String> lps = lm.getAllProviders();
			Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (location != null)
				Toast.makeText(ApkMonitorActivity.this, location.toString(), Toast.LENGTH_LONG);
			else
				Toast.makeText(ApkMonitorActivity.this, "No known last position.", Toast.LENGTH_LONG);
			
		    intent = new Intent("com.rx201.apkmon.ProximityAlert");     
		    PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0); 
		    lm.addProximityAlert(123, 123, 10, -1, pIntent);
		    
		    lm.requestLocationUpdates("gps", 0, 0, new LocationListener() {

				@Override
				public void onLocationChanged(Location location) {
					Toast.makeText(ApkMonitorActivity.this, "Location update: " + location.toString(), Toast.LENGTH_LONG);
				}

				@Override
				public void onStatusChanged(String provider, int status,
						Bundle extras) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void onProviderEnabled(String provider) {
					// TODO Auto-generated method stub
					
				}

				@Override
				public void onProviderDisabled(String provider) {
					// TODO Auto-generated method stub
					
				}
		    	
		    });
			break;
		case R.id.btnReflectionTest:
			Toast.makeText(this, "FINGERPRINT: " + Build.FINGERPRINT, 500).show();
	    	try {
				Class clsApiHook = Class.forName("com.rx201.apkmon.APIHook");
				Field fldApiHookApp = clsApiHook.getField("app");
				fldApiHookApp.set(null, null);

			} catch (Exception e) {
				Toast.makeText(this, "Catch " + e.toString() + "when accessing 'app' field.", 500).show();
			}
	    	try {
				Class clsApiHook = Class.forName("com.rx201.apkmon.APIHook");
				Method mtdApiHookIsTesting = clsApiHook.getMethod("isTesting", null);
				mtdApiHookIsTesting.invoke(null, null);

			} catch (Exception e) {
				Toast.makeText(this, "Catch " + e.toString() + "when invoking 'isTesting' method.", 500).show();
			}
		}
	    	
	}
    


}