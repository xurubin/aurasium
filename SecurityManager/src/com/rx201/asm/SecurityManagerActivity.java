package com.rx201.asm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.rx201.apkmon.permissions.PermissionRequestIntent;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

class AppListAdapter extends ArrayAdapter<String> {
	private final HashMap<String, List<PermissionRequestIntent>> values;

	public AppListAdapter(Context context,
			HashMap<String, List<PermissionRequestIntent>> values) {
		super(context, 0, values.keySet().toArray(
				new String[values.size()]));
		this.values = values;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.app_entry_layout, parent,
				false);
		TextView nameTextView = (TextView) rowView
				.findViewById(R.id.appentry_name);
		TextView descTextView = (TextView) rowView
				.findViewById(R.id.appentry_description);
		ImageView imageView = (ImageView) rowView
				.findViewById(R.id.appentry_image);

		String appPackage = getItem(position);
		final PackageManager pm = getContext().getPackageManager();
		ApplicationInfo info;
		try {
			info = pm.getApplicationInfo(appPackage, 0);
			CharSequence caption = pm.getApplicationLabel(info);
			if (caption != null)
				nameTextView.setText(caption);
			else
				nameTextView.setText(appPackage);
			imageView.setImageDrawable(pm.getApplicationIcon(info));
			descTextView.setText(String.format(
					"%d remembered policy decisions.", values
							.get(appPackage).size()));
		} catch (NameNotFoundException e) {
			nameTextView.setText(appPackage);
			descTextView.setText("Cannot retrieve application information.");
		}

		return rowView;
	}

}

public class SecurityManagerActivity extends ListActivity {
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		String application = (String) this.getListAdapter().getItem(position);
		if (application != null) {
			Intent intent = new Intent(SecurityManagerActivity.this,
					PolicyEditActivity.class);
			intent.putExtra("application", application);
			startActivity(intent);
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setListAdapter(new AppListAdapter(this, PolicyStorage.get(this).getAllPoliciesDict()));
	}

	private void populateTestData() {
		final PackageManager pm = getPackageManager();
		HashMap<String, List<PermissionRequestIntent>> test = new HashMap<String, List<PermissionRequestIntent>>();
		for (ApplicationInfo app : pm
				.getInstalledApplications(PackageManager.GET_META_DATA)) {
			ArrayList<PermissionRequestIntent> v = new ArrayList<PermissionRequestIntent>();
			for (int i = 0; i < app.uid % 10; i++)
				v.add(null);
			test.put(app.packageName, v);
		}
		setListAdapter(new AppListAdapter(this, test));
	}
}