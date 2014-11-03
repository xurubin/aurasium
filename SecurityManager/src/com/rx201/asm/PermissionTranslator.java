package com.rx201.asm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import android.util.Base64;

import com.rx201.apkmon.permissions.AurasiumPermission;

public class PermissionTranslator {
	public static String PermissionToString(AurasiumPermission p) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ObjectOutputStream objOut = new ObjectOutputStream(out);
	        objOut.writeObject(p);
	        objOut.close();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String content = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);
		return String.format("%s:%s.%s$%s", p.getClass().getName(), p.getPermissionIdentifier(), p.getGroupingIdentifier(), content);
	}
	
	public static AurasiumPermission StringToPermission(String s) {
		AurasiumPermission result = null;
		int sep = s.indexOf("$");
		if (sep == -1)
			return null;
		ByteArrayInputStream in = new ByteArrayInputStream(Base64.decode(s.substring(sep + 1), Base64.DEFAULT));
			try {
				ObjectInputStream objIn = new ObjectInputStream(in);
				result = (AurasiumPermission)objIn.readObject();
				objIn.close();
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		return result;
	}
}
