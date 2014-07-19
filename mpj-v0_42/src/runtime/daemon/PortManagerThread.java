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
 * File         : PortManagerThread.java 
 * Author       : Khurram Shahzad, Mohsan Jameel, Aamir Shafi, Bryan Carpenter
 * Created      : Oct 27, 2013
 * Revision     : $
 * Updated      : Nov 05, 2013 
 */

package runtime.daemon;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class PortManagerThread extends Thread {
  public volatile boolean isRun = true;

  public PortManagerThread() {

  }

  private int port = getServerSocketPortFromWrapper();;

  @Override
  public void run() {
    serverSocketInit();
  }

  private void serverSocketInit() {

    try {

      ServerSocket servSock = new ServerSocket(port);
      while (isRun) {
	Socket sock = servSock.accept();
	PortRequesterThread thread = new PortRequesterThread(sock);
	thread.start();
      }

    }
    catch (Exception cce) {

      System.exit(0);
    }

  }

  private static int getServerSocketPortFromWrapper() {

    int port = 0;
    FileInputStream in = null;
    DataInputStream din = null;
    BufferedReader reader = null;
    String line = "";

    try {

      String path = System.getenv("MPJ_HOME") + "/conf/wrapper.conf";
      in = new FileInputStream(path);
      din = new DataInputStream(in);
      reader = new BufferedReader(new InputStreamReader(din));

      while ((line = reader.readLine()) != null) {
	if (line.startsWith("wrapper.app.parameter.3")) {
	  String trimmedLine = line.replaceAll("\\s+", "");
	  port = Integer.parseInt(trimmedLine.substring(24));
	  break;
	}
      }

      in.close();

    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return port;

  }

}
