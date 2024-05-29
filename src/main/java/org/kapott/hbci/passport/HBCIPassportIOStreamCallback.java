package org.kapott.hbci.passport;

import java.io.InputStream;
import java.io.OutputStream;

public interface HBCIPassportIOStreamCallback {

	InputStream getInputStream();
	
	OutputStream getOutputStream();
	
	
}
