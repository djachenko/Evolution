package runtime.daemon;

/*
 The MIT License

 Copyright (c) 2013 - 2013
 1. High Performance Computing Group, 
 School of Electrical Engineering and Computer Science (SEECS), 
 National University of Sciences and Technology (NUST)
 2. Khurram Shahzad, Mohsan Jameel, Aamir Shafi, Bryan Carpenter (2013 - 2013)


 Permission is hereby granted, free of charge, to any person obtaining
 a copy of this software and associated documentation files (the
 "Software"), to deal in the Software without restriction, including
 without limitation the rights to use, copy, modify, merge, publish,
 distribute, sublicense, and/or sell copies of the Software, and to
 permit persons to whom the Software is furnished to do so, subject to
 the following conditions:

 The above copyright notice and this permission notice shall be included
 in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
/*
 * File         : OutputHandler.java 
 * Author       : Khurram Shahzad, Mohsan Jameel, Aamir Shafi, Bryan Carpenter
 * Created      : Oct 27, 2013
 * Revision     : $
 * Updated      : Nov 05, 2013 
 */
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

class OutputHandler extends Thread {

  Process p = null;
  Socket sock = null;

  public OutputHandler(Process p, Socket cSock) {
    this.p = p;
    sock = cSock;
  }

  public void run() {

    InputStream outp = p.getInputStream();
    String line = "";
    BufferedReader reader = new BufferedReader(new InputStreamReader(outp));

    if (MPJDaemon.DEBUG && MPJDaemon.logger.isDebugEnabled()) {
      MPJDaemon.logger.debug("outputting ...");
    }

    try {
      do {
	if (!line.equals("")) {
	  line.trim();

	  synchronized (this) {
	    line += "\n";
	    OutputStream outToServer = sock.getOutputStream();
	    DataOutputStream out = new DataOutputStream(outToServer);
	    out.write(line.getBytes(), 0, line.getBytes().length);
	    out.flush();
	  }
	}
      } while ((line = reader.readLine()) != null);
      // && !kill_signal);
    }
    catch (Exception e) {
      if (MPJDaemon.DEBUG && MPJDaemon.logger.isDebugEnabled()) {
	MPJDaemon.logger.debug("outputHandler =>" + e.getMessage());
      }
     e.printStackTrace();
    }
  } // end run.
}