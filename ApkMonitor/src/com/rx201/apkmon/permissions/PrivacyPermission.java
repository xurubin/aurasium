package com.rx201.apkmon.permissions;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class PrivacyPermission extends AurasiumPermission  implements Serializable {
	public enum PRIVACY_TYPE {PhoneNumber, IMEI, IMSI, Contact, GPS_Last, GPS_Proximity};
	
	private PRIVACY_TYPE type;
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
		dest.writeString(type.name());
	}
	private PrivacyPermission(Parcel in) {
		super(in);
		type = PRIVACY_TYPE.valueOf(in.readString());
	}

	// if DomainName is null, then use value of RemoteIP as DomainName
	public PrivacyPermission(PRIVACY_TYPE type)
	{
		super();
		this.type = type;

		switch (type) {
		case PhoneNumber : 
			setDescription("Possible privacy violation: this application is trying to access your phone number, allow or not?");
			break;
		case IMEI : 
			setDescription("Possible privacy violation: this application is trying to access IMEI, allow or not?");
			break;
		case IMSI : 
			setDescription("Possible privacy violation: this application is trying to access IMSI, allow or not?");
			break;
		case Contact : 
			setDescription("Possible privacy violation: this application is trying to access contact list, allow or not?");
			break;
		case GPS_Last : 
			setDescription("Possible privacy violation: this application is trying to access your location information, allow or not?");
			break;
		case GPS_Proximity : 
			setDescription("Possible privacy violation: this application is trying to access location information, allow or not?");
			break;
		default:
			setDescription("Possible privacy violation: this application is trying to access ?something?, allow or not");
		}
	}
	
	public static final Parcelable.Creator<PrivacyPermission> CREATOR = new Parcelable.Creator<PrivacyPermission>() {
		public PrivacyPermission createFromParcel(Parcel in) {
			return new PrivacyPermission(in);
		}

		public PrivacyPermission[] newArray(int size) {
			return new PrivacyPermission[size];
		}
	};

	@Override
	public String getPermissionIdentifier() {
		return "privacy";
	}
	@Override
	public String getGroupingIdentifier() {
		return type.name();
	}
	@Override
	public String getGroupingDescription() {
		switch (type) {
		case Contact:
			return "Contact List";
		case GPS_Last:
			return "GPS: Last Position";
		case GPS_Proximity:
			return "GPS: Proximity Alert";
		default:
			return type.name();
		}
	}
	@Override
	public boolean equals(Object o) {
		if (o != null && o instanceof PrivacyPermission)
			return type.equals(((PrivacyPermission)o).type);
		else
			return false;
	}

}
