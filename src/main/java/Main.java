import org.productivity.java.syslog4j.Syslog;
import org.productivity.java.syslog4j.SyslogIF;

import java.io.*;

public class Main extends Thread {
    public Status status;

    public void run() {
        System.out.println("thread run.");

        status = Status.CHECK_KRAKEN;

        while(true){
            System.out.println("STATUS : " + status);
            switch (status){
                case CHECK_KRAKEN:
                    String result = command("netstat -antp|grep 514");
                    if (result == null){
                        System.out.println("Kraken DOWN");
                        status = Status.KRAKEN_DOWN;
                    }else if (result.contains("java")){
                        System.out.println("Kraken alive");
                        status = Status.KRAKEN_UP;
                    }else{
                        System.out.println("Kraken DOWN");
                        status = Status.KRAKEN_DOWN;
                    }
                    break;
                case KRAKEN_DOWN:
                    if(!isFileExist("/var/log/syslog")){
                        command("cp -f ./syslog_keep_log /etc/rsyslog");
                        command("systemctl restart rsyslog");
                    }

                    try {
                        status = Status.CHECK_KRAKEN;
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                case KRAKEN_UP:
                    if(isFileExist("/var/log/syslog")){
                        command("cp -f ./syslog_send_kraken /etc/rsyslog");
                        command("systemctl restart rsyslog");
                        status = Status.SEND_LOG;
                    }else{
                        try {
                            status = Status.CHECK_KRAKEN;
                            sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case SEND_LOG:
                    sendLog();
                    command("rm /var/log/syslog");
                    try {
                        status = Status.CHECK_KRAKEN;
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    try {
                        status = Status.CHECK_KRAKEN;
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }

    public static void main(String[] args) {
        Main main = new Main();
        main.start();
    }

    public String command(String msg){
        String s = null;
        StringBuilder stringBuilder = new StringBuilder();
        Process p;
        try {
            String[] cmd = {msg};
            p = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((s = br.readLine()) != null){
                stringBuilder.append(s);
            }
            p.waitFor();
            p.destroy();
            return stringBuilder.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isFileExist(String msg){

        File f = new File(msg);
        if(f.exists()) {
            return true;
        } else {
            return false;
        }
    }

    public void sendLog(){
        SyslogIF client = Syslog.getInstance("udp");
        client.getConfig().setHost("127.0.0.1");
        client.getConfig().setPort(514);
        client.getConfig().setFacility(15);
        try{
            File file = new File("/var/log/syslog");
            FileReader filereader = new FileReader(file);
            BufferedReader bufReader = new BufferedReader(filereader);
            String line = "";
            while((line = bufReader.readLine()) != null){
                client.info(line);
            }
            bufReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

enum Status {
    CHECK_KRAKEN,
    KRAKEN_DOWN,
    KRAKEN_UP,
    SEND_LOG,
}

