import edu.cornell.cs.sam.io.SamTokenizer;
import edu.cornell.cs.sam.io.Tokenizer.TokenType;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class BaliCompiler {

    static HashMap<String, Integer> variables = new HashMap<>(); // position of variable in method
    static HashMap<String, HashMap<String, Integer>> methods = new HashMap<>(); // number of variables in method
    static int label_num = 0;
    static int loop_num = 0;
    static int actual_num = 0;
    static int var_num = 0;
    static int param_num = 0;
    static int inWhile = 0;
    static final String nop = "NOP\n";

    static String compiler(String fileName) {
        // returns SaM code for program in file
        try {
            SamTokenizer f = new SamTokenizer(fileName);
            String pgm = getProgram(f);
            return pgm;
        } catch (Exception e) {
            System.err.println(e);
            return "HLT\n";
        }
    }

    static String getProgram(SamTokenizer f) {
        try {
            String pgm = "";
            while (f.peekAtKind() != TokenType.EOF) {
                variables = new HashMap<>();
                pgm += getMethod(f);
            }

            // need to add start statement for the program
            String start = "%include \"io.inc\"\nsection .data\nans db 'Result is ', 0\nsection .text\nglobal CMAIN\nCMAIN:\npush ebp\nmov ebp, esp\ncall main1\nPRINT_STRING ans\nPRINT_DEC 4, eax\nNEWLINE\npop ebp\nret\n";
            return start + pgm;
        } catch (Exception e) {
            System.out.println(e);
            return "HLT\n";
        }
    }

    static String getMethod(SamTokenizer f) throws Exception {
        // Since the only data type is an int, you can safely check for int
        // in the tokenizer.
        String method_call_convention = "push ebp\nmov ebp, esp\n"; // store the current base pointer, add the new stack
                                                                    // pointer onto the stack.

        // TODO: add appropriate exception handlers to generate useful error msgs.
        var_num = 0;
        param_num = 0;

        // must start with a type (int is the only valid type)
        f.match("int");
        String methodName = f.getWord();
        System.out.println("Compiling code for method: " + methodName);

        if (methodName.equals("main"))
            methodName = "main1";

        f.match('('); // must be an opening parenthesis
        String formals = getFormals(f);
        f.match(')'); // must be an closing parenthesis

        System.out.println("Found " + variables.size() + " formal(s) in method.");

        // now we need to check if this is a body
        f.match('{');
        String body = getBody(f);
        f.match('}');

        System.out.println("Finished compiling method.");
        System.out.println();

        methods.put(methodName, variables);

        System.out.println(methods);
        System.out.println();

        return methodName + ": " + method_call_convention + formals + body;
    }

    static String getExp(SamTokenizer f) {
        switch (f.peekAtKind()) {
            case INTEGER: // E -> integer
                return "mov eax, " + f.getInt() + "\n";
            case WORD: {
                // TODO: implement variables, and method calls
                String in = f.getWord();

                if (f.test('(')) {
                    // this is a method call!
                    // need to put the actuals onto the stack
                    f.match('(');
                    actual_num = 0;
                    String actuals = getActuals(f);
                    // String startReq = "PUSHIMM 0\n";
                    String endReq = "call " + in + "\nadd esp, " + actual_num * 4 + "\n";
                    // decrease
                    f.match(')');
                    // return startReq + actuals + endReq;
                    return actuals + endReq;
                } else if (variables.get(in) != null) {
                    // this is a variable expression!
                    return "mov eax, [ebp" + (variables.get(in) < 0 ? variables.get(in)
                            : "+" + ((param_num - 1
                                    - variables.get(in)) * 4
                                    + 8))
                            + "]\n";
                } else if (in.equals("true")) {
                    return "push dword 1\n";
                } else if (in.equals("false")) {
                    return "push dword 0\n";
                }
            }
            case OPERATOR: {
                String res = "";
                f.match('('); // open paren.
                if (f.test('-')) {
                    f.skipToken();
                    // need to take the next expression, and sub it from 0
                    String nextexp = "mov ebx, 0\n" + getExp(f) + "sub ebx, eax\nmov eax, ebx\n";
                    // nextexp += "PUSHIMM 0\nSUB\n";
                    res = nextexp; // subtract the expression result from 0, and then we can push that onto the
                                   // stack.
                } else if (f.test('!')) {
                    f.skipToken();
                    String nextexp = getExp(f);
                    nextexp += "not eax\nand eax, 1\n"; // and with 0x1
                    res = nextexp;
                } else {
                    String exp = getExp(f);
                    res = exp;

                    if (!f.test(')')) {
                        // this is not a single expression in paren.
                        // add eax to stack
                        // move eax to ebx
                        // pop stack to eax
                        char op = f.getOp();

                        res += "push eax\n";
                        String nextexp = getExp(f);
                        res += nextexp;
                        res += "mov ebx, eax\n";
                        res += "pop eax\n";

                        switch (op) {
                            case '+':
                                res += "add eax, ebx\n";
                                break;
                            case '-':
                                res += "sub eax, ebx\n";
                                break;
                            case '*':
                                res += "imul eax, ebx\n";
                                break;
                            case '/':
                                res += "mov edx, 0\ncdq\nidiv ebx\n";
                                break;
                            case '&':
                                // AND instruction to do bitwise and
                                res += "and eax, ebx\n";
                                break;
                            case '|':
                                res += "or eax, ebx\n";
                                break;
                            case '<':
                                res += "cmp eax, ebx\n";
                                res += "setl al\n";
                                res += "cmp al, 0\n"; // compare to zero (will jump if not zero)
                                break;
                            case '>':
                                res += "cmp eax, ebx\n";
                                res += "setg al\n";
                                res += "cmp al, 0\n"; // compare to zero (will jump if not zero)
                                break;
                            case '=':
                                res += "cmp eax, ebx\n";
                                res += "sete al\n";
                                res += "cmp al, 0\n"; // compare to zero (will jump if not zero)
                                break;
                        }
                    }
                }

                f.match(')'); // matching close paren.
                return res;
            }
            default:
                return "ERROR\n";
        }
    }

    static String getActuals(SamTokenizer f) {
        String res = "";

        while (!f.test(')')) {
            // we have more actuals going on
            res += getExp(f);
            res += "push eax\n";
            actual_num++;
            if (f.test(','))
                f.skipToken();
        }

        return res;
    }

    static String getBody(SamTokenizer f) throws Exception {
        String res = "";
        System.out.println("Compiling method body...");
        boolean donewithdecs = false;
        // keep going until done with the method body
        while (!f.test('}')) {
            if (f.test("int")) {
                if (donewithdecs) {
                    throw new Exception("Cannot declare variables outside top of method.");
                }
                // variable declaration(s)
                System.out.println("Compiling Declarations...");
                String decs = getDeclarations(f);
                res += decs; // add them directly, no special code to be added.
                f.skipToken();
            } else {
                donewithdecs = true;

                res += getStatement(f);
            }
        }

        return res;
    }

    static String getDeclarations(SamTokenizer f) throws Exception {
        String res = "";
        f.match("int"); // only one type
        int pos = -4 - var_num * 4;

        while (f.peekAtKind() == TokenType.WORD) {
            // keep iterating until we reach the semicolon ';'
            String var = f.getWord();
            if (variables.get(var) != null) {
                throw new Exception("Cannot continue. Multiple declarations of the same variable, line " + f.lineNo());
            }

            if (var.equals("true") || var.equals("false")) {
                throw new Exception("Cannot have variable with reserved keywords named 'true' or 'false'.");
            }

            variables.put(var, pos);
            var_num++;

            if (f.test('=')) {
                // if declaring the value
                f.match('=');
                res += getExp(f);
                res += "push eax\n"; // add the expression's result to the stack
                pos -= 4;
                // res += "PUSHOFF " + pos++ + "\n";
            } else {
                res += "push dword 0\n"; // add placeholder at pos
                pos -= 4;
            }

            if (f.test(',')) { // keep going
                f.skipToken();
            }
        }
        return res;
    }

    static String getStatement(SamTokenizer f) throws Exception {
        String res = "";

        if (f.peekAtKind() == TokenType.WORD) {
            // can be return, if-else, while, break, or variable value update.
            String in = f.getWord();
            String exp;

            if (variables.get(in) != null) {
                // this is indeed a variable, we need to update the value
                f.match('='); // assignment must happen
                exp = getExp(f);
                f.match(';');
                return exp + "mov [ebp" + (variables.get(in) < 0 ? variables.get(in)
                        : "+" + ((param_num - 1
                                - variables.get(in)) * 4
                                + 8))
                        + "], eax\n";
            }

            switch (in) {
                case "return":
                    exp = getExp(f);
                    res = exp + "mov esp, ebp\npop ebp\nret\n"; // TODO: make
                                                                // sure works
                                                                // wtih
                                                                // recursive
                                                                // functions
                    f.match(';');
                    break;
                case "if":
                    // if else
                    f.match('(');
                    exp = getExp(f);
                    f.match(')');
                    String truestatement = getStatement(f);
                    f.match("else");
                    String falsestatement = getStatement(f);
                    truestatement = "t" + label_num + ": " + truestatement;
                    String ifelse = exp + "jnz t" + label_num + "\n" + falsestatement + "jmp end" + label_num + "\n"
                            + truestatement + "end" + label_num + ": " + nop;
                    label_num++;
                    return ifelse;

                case "while":
                    f.match('(');
                    exp = getExp(f);
                    f.match(')');
                    inWhile += 1;
                    String statement = getStatement(f);
                    inWhile -= 1;
                    System.out.println("statement is:\n" + statement);
                    String labelStart = "loop" + loop_num + ": ";
                    String protectionClause = exp + "jz loopEnd" + loop_num + "\n";
                    String labelEnd = "loopEnd" + loop_num + ": " + nop;
                    String completeLoop = labelStart + protectionClause + statement + "jmp loop" + loop_num + "\n"
                            + labelEnd;
                    loop_num++;
                    return completeLoop;

                case "break":
                    if (inWhile == 0) {
                        throw new Exception("Cannot use break outside of while loop.");
                    }

                    f.match(';');
                    return "jmp loopEnd" + loop_num + "\n";

                default:
                    throw new Exception("Invalid statement.");
            }
        } else {
            if (f.test('{')) {
                // is a block
                f.match('{');
                while (!f.test('}')) {
                    res += getStatement(f);
                }

                f.match('}');

                return res;
            } else {
                // f.match(';');
                f.skipToken();
            }
        }

        return res;
    }

    static String getAssignment(SamTokenizer f) {
        f.match("=");

        return getExp(f);
    }

    static String getFormals(SamTokenizer f) throws Exception {
        String res = "";
        // these go before the method is actually called, so the stuff is stored before
        // the frame base register.
        int pos = 0; // technically need to know the opposite order
        while (f.test("int")) {
            // we have a formal
            f.match("int");
            String var = f.getWord();

            if (var.equals("true") || var.equals("false")) {
                throw new Exception("Cannot have variable with reserved keywords named 'true' or 'false'.");
            }

            if (variables.get(var) != null) {
                throw new Exception("Cannot continue. Multiple formals with the same name on line " + f.lineNo());
            }

            // put parameters into local variables, might be unneeded tbh
            // res += "mov eax, [ebp+" + pos * 8 + "]\npush eax\n";

            // add the variable and the position it should be at.
            variables.put(var, pos);
            pos += 1;
            param_num++;

            if (f.test(',')) { // keep going
                f.skipToken();
            }
        }
        // variables.put("rv", pos);
        return res;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: BaliCompiler <input_file> <output_file>");
            System.exit(1); // error occured, sysexit status 1
        }

        String inputFile = args[0];
        String outputFile = args[1];

        System.out.println("Beginning Compilation for file " + inputFile + "... \n");

        try {
            String samCode = compiler(inputFile);

            if (samCode.startsWith("HLT")) {
                throw new Exception("Errors during compilation. Output not saved.");
            }

            // Write the SaM code to the output file
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(samCode);
            } catch (IOException e) {
                System.err.println("Error writing to output file: " + e.getMessage());
                System.exit(1);
            }

            System.out.println("Compilation complete. x86 code written to " + outputFile);

        } catch (Exception e) {
            System.err.println("Fatal error during compilation: " + e.getMessage());
            System.exit(1);
        }
    }
}