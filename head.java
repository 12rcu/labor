import java.io.*;
import java.util.Scanner;

public class head {

    public static void main(String[] args) {
        if (args.length < 1)
            ShellLogger.log("red", "Not enough arguments use --help to get help");
        File toOpenFile = null;
        Integer readBytes = null;
        int readLines = 10;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-c":
                    readLines = Integer.parseInt(args[i + 1]);
                    break;
                case "-n":
                    readBytes = Integer.parseInt(args[i + 1]);
                    break;
                case "--help":
                    ShellLogger.log("blue", "help:");
                    //todo print help
                    break;
                default:
                    File file = new File(args[i]);
                    if (file.exists()) {
                        toOpenFile = file;
                    }
                    //do nothing as this may be an argument from -c or -n
                    break;
            }
        }
        if(toOpenFile == null) {
            ShellLogger.log("red", "Could not find file/no file provided");
        } else {
            try {
                if(readBytes != null) {
                    readNBytes(toOpenFile, readBytes);
                } else {
                    readNLines(toOpenFile, readLines);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void readNLines(File target, int lines) throws FileNotFoundException {
        Scanner fileReader = new Scanner(target);
        for (int i = 0; i < lines; i++) {
            ShellLogger.log("green", fileReader.nextLine());
        }
    }

    private static void readNBytes(File file, int bytes) throws IOException {
        byte[] buffer = new byte[bytes];
        FileInputStream fis = new FileInputStream(file);
        fis.read(buffer);
        fis.close();
        ShellLogger.log("green", new String(buffer));
    }

    private static void readNLines(FileDescriptor target, int lines) throws IOException {
        BufferedReader read = new BufferedReader(new FileReader(target));
        for (int i = 0; i < lines; i++) {
            ShellLogger.log("green", read.readLine());
        }
    }

    private static void readNBytes(FileDescriptor file, int bytes) throws IOException {
        byte[] buffer = new byte[bytes];
        FileInputStream fis = new FileInputStream(file);
        fis.read(buffer);
        fis.close();
        ShellLogger.log("green", new String(buffer));
    }
}
