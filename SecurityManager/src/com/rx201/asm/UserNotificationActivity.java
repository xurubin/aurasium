package com.rx201.asm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;

import com.rx201.apkmon.permissions.PermissionRequestIntent;


public class UserNotificationActivity extends Activity implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {
	//TODO: Not needed really as we are not a single top yet. 
	private LinkedList<PermissionRequestIntent> UserRequests = new LinkedList<PermissionRequestIntent>();
	private PermissionRequestIntent CurUserRequest = null;
	private boolean RememberDecision = false;
	
	private AlertDialog dlg;
	private Handler mHandler = new Handler();
	// TODO: When default choice with countdown is used, there is a bug that when the user
	// quickly click a dialog button right after the dialog is shown, the dialog is dismissed
	// with the defaultChoice instead of user's intended action.
	private boolean CountdownStopped = false;
	@Override
	protected Dialog onCreateDialog(int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(CurUserRequest.getPromptText())
		       .setCancelable(false)
		       .setTitle(CurUserRequest.getApplicationPackage())
		       .setPositiveButton("Yes", this)
		       .setNeutralButton("No", this)
		       .setNegativeButton("Kill App", this);
		
		LinearLayout linearLayout = new LinearLayout(this);
		linearLayout.setLayoutParams( new  LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
		                LinearLayout.LayoutParams.FILL_PARENT));
		linearLayout.setOrientation(1);     

		CheckBox checkBox = new CheckBox(this);
		checkBox.setText("Remember this decision for: " + CurUserRequest.getGroupingDescription());
		checkBox.setChecked(RememberDecision);
		checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				RememberDecision = isChecked;
			}
		});
		linearLayout.addView(checkBox);
		
		//Requires to display threat level information about some IP address.
		if (CurUserRequest.getRemoteIP() != null)
		{
			TextView tvIpAddr = new TextView(this);
			tvIpAddr.setText("IP Address: " + CurUserRequest.getRemoteIP());
			linearLayout.addView(tvIpAddr);

			final TextView tvIpInfo = new TextView(this);
			tvIpInfo.setText("Retrieving information about this address...");
			Thread IpLookupThread = new Thread(new Runnable() {
				public void run() {
					try{
		            	URL url = new URL("http://kb.bothunter.net/ipInfo/IPRep.php?IP=" +  CurUserRequest.getRemoteIP() +"&FORMAT=TAB");
		                URLConnection conn = url.openConnection();
		                // Get the response
		                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		                String line = "";
		                String alllines = "";
		                while ((line = rd.readLine()) != null) {
		                	alllines = alllines + line;
		                }
		                String THREAT_LEVEL = "Unknown";
		                String THREAT_CATEGORY = "Unknown";
		                String COUNTRY = "Unknown";
		                for (String item : alllines.split("\t"))
		                {
		                	if (item.startsWith("THREAT_LEVEL"))
		                		THREAT_LEVEL = item.substring(13);
		                	else if (item.startsWith("THREAT_CATEGORY"))
		                		THREAT_CATEGORY = item.substring(16);
	                		else if (item.startsWith("COUNTRY"))
	                			COUNTRY = item.substring(8);
		                }
		                if (THREAT_CATEGORY.length() > 0)
		                	THREAT_CATEGORY = " (" + THREAT_CATEGORY + ")";
		                final String IpDesc = String.format("Threat Level: %s%s\r\nCountry: %s", THREAT_LEVEL, THREAT_CATEGORY, COUNTRY);
						tvIpInfo.post(new Runnable() {
							public void run() {
								tvIpInfo.setText(IpDesc);
							}
						});
					}catch(IOException e)
					{
						tvIpInfo.post(new Runnable() {
							public void run() {
								tvIpInfo.setText("Retrieval failed.");
							}
						});
					}
				}
			});
			IpLookupThread.start();
			linearLayout.addView(tvIpInfo);
		}

		builder.setView(linearLayout);
		
		dlg = builder.create();
		dlg.setOnDismissListener(this);
		
		if (CurUserRequest.getDefaultDelay() != 0)
		{
			CountdownStopped = false;
			mHandler.postDelayed(new Runnable() {
				public void run() {
					if (dlg.isShowing() && !CountdownStopped)
					{
						int remainingCountdown = CurUserRequest.getDefaultDelay();
						if (remainingCountdown <= 1) //Timeout, go for default choice.
						{
   					        Log.i("apihook_Dialog", "Default decision");
							CompleteCurrentUserRequest(CurUserRequest.getDefaultChoice());
							dismissDialog(0);
							ProcessingPendingDialogRequests();
						}
						else
						{
							remainingCountdown -= 1;
							int BtnID;
							String BtnText;
							if (CurUserRequest.getDefaultChoice() > 0)
							{
								BtnID = DialogInterface.BUTTON_POSITIVE;
								BtnText = String.format("Yes (%d)", remainingCountdown);
							}
							else
							{
								BtnID = DialogInterface.BUTTON_NEUTRAL;
								BtnText = String.format("No (%d)", remainingCountdown);
							}
							dlg.getButton(BtnID).setText(BtnText);
							mHandler.postDelayed(this, 1000);
						}
					}
				}
			}, 1000);
		}
		return dlg;
	}

	private void EnqueueNewIntent(Intent intent){
		Log.i("apihook_Dialog", "EnqueueNewIntent()");
		PermissionRequestIntent req = new PermissionRequestIntent(intent);
		UserRequests.addLast(req);
	}
	private void CompleteCurrentUserRequest(int msgArg1){
       Log.i("apihook_Dialog", "CompleteCurrentUserRequest()");
 	   Message msg = Message.obtain();
	   msg.what = CurUserRequest.getResponseToken();
	   msg.arg1 = msgArg1;
	   msg.arg2 = RememberDecision ? 1 : 0;
	   try {
		   CurUserRequest.getResultMessenger().send(msg);
	   } catch (RemoteException e) {
		   e.printStackTrace();
	   } finally {
		   msg.recycle();
	   }
	}
	
	//TODO: Pending requests is not currently working (not showing dialogs). Luckily we don't need it right now
	// because each request fires up a new activity instance.
	private void ProcessingPendingDialogRequests(){
        Log.i("apihook_Dialog", "ProcessingPendingDialogRequests()");
		if (UserRequests.size() == 0)
		{
			CurUserRequest = null;
			finish();
		}
		else
		{
			CurUserRequest = UserRequests.removeFirst();
			showDialog(0);
		}
	}

//TODO: Not needed really as we are not a single top yet. 
//	@Override
//	protected void onNewIntent(Intent intent) {
//		if (intent.getAction().equals("ACTION_SHOW_DIALOG"))
//			EnqueueNewIntent(intent);
//		else
//			super.onNewIntent(intent);
//	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null)
		{
			// The system is recreating the dialog, possibly because of 
			// a screen orientation change. 
			RememberDecision = savedInstanceState.getBoolean("RememberDecision");
			CurUserRequest  = (PermissionRequestIntent)savedInstanceState.getParcelable("CurReq");
			UserRequests.clear();
			for (int i=0;i<savedInstanceState.getInt("PendingRequests");i++)
				UserRequests.addLast((PermissionRequestIntent)savedInstanceState.getParcelable("PendingReq" + i));
		}
		else
		{
			EnqueueNewIntent(getIntent());
			ProcessingPendingDialogRequests();
		}			
		
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putBoolean("RememberDecision", RememberDecision);
		outState.putParcelable("CurReq", CurUserRequest);
		outState.putInt("PendingRequests", UserRequests.size());
		for (int i=0;i<UserRequests.size();i++)
			outState.putParcelable("PendingReq" + i, UserRequests.removeFirst());
	}

	//Callback when the dialog button is clicked.
	public void onClick(DialogInterface dialog, int which) {
		if (CurUserRequest == null)
			return;
		CountdownStopped = true;
        Log.i("apihook_Dialog", "User Consent" + CurUserRequest.getResultMessenger().toString());
		if (which == DialogInterface.BUTTON_POSITIVE) // Yes button
			new GrantPermissionHandler().setRemembered(RememberDecision).setContext(this).handlePermissionCheck(CurUserRequest);
//			CompleteCurrentUserRequest(1);
		else if (which == DialogInterface.BUTTON_NEUTRAL) // No button
			new DenyPermissionHandler().setRemembered(RememberDecision).setContext(this).handlePermissionCheck(CurUserRequest);
//			CompleteCurrentUserRequest(0);
		else /*if (which == DialogInterface.BUTTON_NEGATIVE)*/ // Close App button
			new KillAppPermissionHandler().setRemembered(RememberDecision).setContext(this).handlePermissionCheck(CurUserRequest);
//			CompleteCurrentUserRequest(-1);
		
		dismissDialog(0);
		ProcessingPendingDialogRequests();
	}

	//Callback when the dialog is closed
	public void onDismiss(DialogInterface dialog) {
//		if (!isFinishing())
//			ProcessingPendingDialogRequests();
	}

	
}
