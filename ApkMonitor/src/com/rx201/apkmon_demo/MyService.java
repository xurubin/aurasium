package com.rx201.apkmon_demo;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import com.rx201.apkmon.demo.IAddResultCallback;
import com.rx201.apkmon.demo.IMyService;

public class MyService extends Service {

	@Override
	public IBinder onBind(Intent intent) {
		if (IMyService.class.getName().equals(intent.getAction())) {
			Log.d("Apihook_service", "Bound by intent " + intent);
			return Api;
		} else {
			return null;
		}
	}
    @Override
    public void onCreate() {
          super.onCreate();
          Log.d("Apihook_service", "Service created ...");
    }
   
    @Override
    public void onDestroy() {
          super.onDestroy();
          Log.d("Apihook_service", "Service destroyed ...");
    }
    
    private IMyService.Stub Api = new IMyService.Stub() {
		
		@Override
		public int add(int i1, int i2, IAddResultCallback onResult)
				throws RemoteException {
			if (onResult != null)
			{
				Log.d("Apihook_service", "Adding");
				return onResult.onResult(i1, i2);
			}
			else
				return -1;
		}
	};

}
