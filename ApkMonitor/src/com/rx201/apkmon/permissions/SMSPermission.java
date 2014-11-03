package com.rx201.apkmon.permissions;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class SMSPermission extends AurasiumPermission  implements Serializable {
	private String telNum;
	private String textContent;
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
		dest.writeString(telNum);
		dest.writeString(textContent);
	}
	private SMSPermission(Parcel in) {
		super(in);
		telNum = in.readString();
		textContent = in.readString();
	}

	private static String ShortenText(String Text)
	{
		if(Text == null)
			return null;
		else if (Text.length() <= 10)
			return Text;
		else
			return Text.substring(0, 7) + "...";
	}

	private static String generateDescription(String SMSNumber, String SMSContent)
	{
		String PremiumTip = "";
		if (SMSNumber.length() <= 6)
			PremiumTip = ", which may be a premium rate number";
		return "This application is trying to send a SMS('"+ ShortenText(SMSContent) +"') to " + SMSNumber +  PremiumTip + ", allow or not?";
	}

	public SMSPermission(String SMSNumber, String SMSContent)
	{
		super(generateDescription(SMSNumber, SMSContent));
		telNum = SMSNumber;
		textContent = SMSContent;
	}
	
	public static final Parcelable.Creator<SMSPermission> CREATOR = new Parcelable.Creator<SMSPermission>() {
		public SMSPermission createFromParcel(Parcel in) {
			return new SMSPermission(in);
		}

		public SMSPermission[] newArray(int size) {
			return new SMSPermission[size];
		}
	};

	@Override
	public String getPermissionIdentifier() {
		return "SMS";
	}
	@Override
	public String getGroupingIdentifier() {
		return telNum;
	}
	@Override
	public String getGroupingDescription() {
		return telNum;
	}
	@Override
	public boolean equals(Object o) {
		if (o != null && o instanceof SMSPermission)
		{
			SMSPermission p = (SMSPermission)o;
			return telNum.equals(p.telNum) && textContent.equals(p.textContent);
		}
		else
			return false;
	}

}
