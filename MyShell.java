import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import static cTools.KernelWrapper.STDIN_FILENO;
import static cTools.KernelWrapper.STDOUT_FILENO;
import static cTools.KernelWrapper.*;

public class MyShell {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        //main loop
        while (true) {
            System.out.print("My Shell: $ ");
            try {
                Input input = new Input(scanner.nextLine());
                if (input.isExit())
                    break;
            } catch (Exception e) {
                //should never happen, ->
                //continue if it does
                ShellLogger.log("red", "Severe error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        scanner.close();
    }
}

class Input {
    private final String raw;
    private final ArrayList<PipeSplit> processedPipeSplit = new ArrayList<>();

    private int currentStdin = STDIN_FILENO;
    private int currentStdout = STDOUT_FILENO;

    private boolean exit = false;

    public Input(String raw) {
        this.raw = raw;
        if (raw.equals("exit")) {
            exit = true;
            return;
        }
        setPipeSplit();
        for (int i = 0; i < processedPipeSplit.size(); i++) {
            PipeSplit pipe = processedPipeSplit.get(i);
            pipe.checkPipeConstrains(i == processedPipeSplit.size() - 1);
        }
    }

    private void setPipeSplit() {
        String[] splitRaw = raw.split(" \\| ");
        for (String pipeSplit : splitRaw) {
            processedPipeSplit.add(new PipeSplit(pipeSplit, this));
        }
    }

    public int getCurrentStdin() {
        return currentStdin;
    }

    public void setCurrentStdin(int currentStdin) {
        this.currentStdin = currentStdin;
    }

    public void setCurrentStdout(int currentStdout) {
        this.currentStdout = currentStdout;
    }

    public boolean isExit() {
        return exit;
    }

    public void setExit(boolean exit) {
        this.exit = exit;
    }

    public int getCurrentStdout() {
        return currentStdout;
    }
}

class PipeSplit {
    private final ArrayList<AndSplit> andSplit = new ArrayList<>();
    private final String raw;
    private final Input parentInput;
    private final int[] pipeFd = new int[2];

    public PipeSplit(String raw, Input parentInput) {
        this.raw = raw;
        this.parentInput = parentInput;
        setAndSplit();
    }

    private void setAndSplit() {
        String[] splitRaw = raw.split(" && ");
        for (String split : splitRaw) {
            andSplit.add(new AndSplit(split, this, parentInput));
        }
    }

    /**
     * check pipe constrains and execute & redirect commands
     *
     * @param last a flag to redirect the last command to the std output
     */
    public void checkPipeConstrains(boolean last) {
        pipeFd[0] = STDIN_FILENO;
        pipeFd[1] = STDOUT_FILENO;

        if(!last) {
            if (pipe(pipeFd) < 0) { //exec pipe and set pipe fd
                ShellLogger.log("red", "Error: Pipe failed, pipe fd < 0");
            }
        }
        //System.out.println("Created Pipe: " + pipeFd[0] + " " + pipeFd[1] + ". New stdin: " + parentInput.getCurrentStdin());

        for (AndSplit command : andSplit) {
            command.execCommand();
        }

        parentInput.setCurrentStdin(pipeFd[0]);
        parentInput.setCurrentStdout(pipeFd[1]);
    }

    public int[] getPipeFd() {
        return pipeFd;
    }
}

class AndSplit {
    private final String raw;
    private final ArrayList<String> argSplit = new ArrayList<>();
    private final PipeSplit pipeSplitParent;
    private final Input inputParent;
    private final File service;

    public AndSplit(String raw, PipeSplit pipeParent, Input inputParent) {
        this.raw = raw;
        this.pipeSplitParent = pipeParent;
        this.inputParent = inputParent;
        setArgSplit();
        service = getValidFileForCommand(argSplit.get(0), System.getenv("PATH").split(":"));

        //set the service path as the first arg (command)
        if (service != null) {
            argSplit.set(0, service.getAbsolutePath());
        }
    }

    public void execCommand() {
        if (argSplit.get(0).equals("exit")) {
            inputParent.setExit(true);
            return;
        }
        if (service == null) {
            return;
        }


        int[] status = new int[1];
        int pid = fork();

        if (pid < 0) {
            ShellLogger.log("red", "Error: Cold not fork");
            System.exit(1);
        } else if (pid == 0) {
            //child

            //pipe
            if (inputParent.getCurrentStdin() != STDIN_FILENO && inputParent.getCurrentStdout() != STDOUT_FILENO) {
                close(inputParent.getCurrentStdout());
                dup2(inputParent.getCurrentStdin(), STDIN_FILENO);
                close(inputParent.getCurrentStdin());
                System.out.println("-> Pipe ready for read");
            }

            //write into pipe
            if (pipeSplitParent.getPipeFd()[0] != STDIN_FILENO && pipeSplitParent.getPipeFd()[1] != STDOUT_FILENO) {
                close(pipeSplitParent.getPipeFd()[0]);
                dup2(pipeSplitParent.getPipeFd()[1], STDOUT_FILENO);
                close(pipeSplitParent.getPipeFd()[1]);
                System.out.println("-> Pipe ready for write");
            }

            int split = Math.max(getInRedirect(), getOutRedirect());    //this does more than it seems
            List<String> subCommandList = argSplit;
            if (split >= 0) {
                subCommandList = argSplit.subList(0, split);
            }

            //execute
            String[] arguments = new String[subCommandList.size()];
            subCommandList.toArray(arguments);
            int execStatus = execv(service.getAbsolutePath(), arguments);

            if (execStatus < 0) {
                ShellLogger.log("red", "Error: Could not execute command (beginning with: " + subCommandList.get(0));
            } else {
                ShellLogger.log("green", "Success: Executed command");
            }

        } else {
            //parent
            if (inputParent.getCurrentStdin() != STDIN_FILENO && inputParent.getCurrentStdout() != STDOUT_FILENO) {
                close(inputParent.getCurrentStdin());
                close(inputParent.getCurrentStdout());
            }

            if (waitpid(pid, status, 0) < 0) {
                ShellLogger.log("red", "Error: waiting for child");
                exit(1);
            }
            if (status[0] != 0) {
                ShellLogger.log("red", "Error: Child returned error code");
            }
        }
    }

    private int getOutRedirect() {
        int outIndex = argSplit.indexOf(">");
        if (outIndex != -1) {
            //close stdout and open file in arr[i+1]
            close(STDOUT_FILENO); // 1 = stdout
            int status = open(argSplit.get(outIndex + 1), O_WRONLY | O_CREAT);

            if (status < 0) {
                ShellLogger.log("red", "Error: Could not open/create/write " + argSplit.get(outIndex + 1));
            }
        }
        return outIndex;
    }

    private int getInRedirect() {
        int inIndex = argSplit.indexOf("<");
        if (inIndex != -1) {
            close(STDIN_FILENO);
            int status = open(argSplit.get(inIndex + 1), O_RDONLY);

            if (status < 0) {
                ShellLogger.log("red", "Error: Could not read " + argSplit.get(inIndex + 1));
            }
        }
        return inIndex;
    }

    private void setArgSplit() {
        String[] splitRaw = raw.split(" ");
        argSplit.addAll(Arrays.asList(splitRaw));
    }

    private File getValidFileForCommand(String command, String[] env) {
        for (String s : env) {
            File tmp = new File(s, command);
            if (tmp.exists()) {
                return tmp;
            }
        }
        ShellLogger.log("red", "No command found: " + command);
        return null;
    }
}

class ShellLogger {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";

    private ShellLogger() {
        //Singleton pattern
    }

    public static void log(String color, String value) {
        switch (color) {
            case "red":
                System.out.println(ANSI_RED + value + ANSI_RESET);
                break;
            case "blue":
                System.out.println(ANSI_BLUE + value + ANSI_RESET);
                break;
            case "green":
                System.out.println(ANSI_GREEN + value + ANSI_RESET);
                break;
            default:
                System.out.println(value);
        }
    }
}