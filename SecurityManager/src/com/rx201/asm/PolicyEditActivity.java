package com.rx201.asm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.rx201.apkmon.permissions.AurasiumPermission;
import com.rx201.apkmon.permissions.PermissionRequestIntent;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
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
import java.util.Comparator;
class AppPolicyListAdapter extends ArrayAdapter<PermissionRequestIntent> {
	private final HashMap<PermissionRequestIntent, String> values;

	public AppPolicyListAdapter(Context context, HashMap<PermissionRequestIntent, String> policies) {
		super(context, 0, policies.keySet().toArray(new PermissionRequestIntent[policies.size()]));
		this.values = policies;
	}

	private static String getText(AurasiumPermission permission) {
		return String.format("%s: %s", permission.getPermissionIdentifier(), permission.getGroupingDescription());
	}
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.policy_edit_layout, parent, false);
		TextView nameTextView = (TextView) rowView.findViewById(R.id.policy_title);
		ImageView imageView = (ImageView) rowView.findViewById(R.id.policy_image);
		
		AurasiumPermission permission = getItem(position).getPermissionRequest();
		String decision = values.get(getItem(position));
		nameTextView.setText(getText(permission));
		if (decision.contains("Grant"))
			imageView.setImageResource(R.drawable.allow);
		else if (decision.contains("Deny"))
			imageView.setImageResource(R.drawable.deny);
		else if (decision.contains("Kill"))
			imageView.setImageResource(R.drawable.kill);
		else if (decision.contains("Consent"))
			imageView.setImageResource(R.drawable.ask);
//		imageView.setImageDrawable(pm.getApplicationIcon(info));
		return rowView;
	}
	
	public void sort() {
		sort(new Comparator<PermissionRequestIntent>() {
						public int compare(PermissionRequestIntent object1, PermissionRequestIntent object2) {
							String s0 = getText(object1.getPermissionRequest());
							String s1 = getText(object2.getPermissionRequest());
							return s0.compareTo(s1);
						}
		});
	}
} 


public class PolicyEditActivity extends ListActivity {
	private void populateContent() {
        HashMap<PermissionRequestIntent, String> p = PolicyStorage.get(this).getAllPolicies(getIntent().getStringExtra("application"));
        AppPolicyListAdapter adapter = new AppPolicyListAdapter(this, p);
        adapter.sort();
        setListAdapter(adapter);
		
	}
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        populateContent();
    }

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		this.showDialog(position);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		final PermissionRequestIntent permission = (PermissionRequestIntent) this.getListAdapter().getItem(id);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Choose an action");
		final String[] AllActions = PermissionHandlerFactory.handlerDescriptions;
		String currentAction = PermissionHandlerFactory.DecisionToDescription(PolicyStorage.get(this).getPolicy(permission));
		int index = -1;
		for(int i=0; i< AllActions.length; i++)
			if (currentAction.equals(AllActions[i]))
				index = i;
		builder.setSingleChoiceItems(AllActions, index, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		    	PolicyStorage.get(PolicyEditActivity.this).setPolicy(permission, PermissionHandlerFactory.DescriptionToDecision(AllActions[item]));
		    	populateContent();
		    	//((AppPolicyListAdapter)PolicyEditActivity.this.getListAdapter()).notifyDataSetChanged();
		    	dialog.dismiss();
		    }
		});
		AlertDialog alert = builder.create();
		return alert;
	}
}
