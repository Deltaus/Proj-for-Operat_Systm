#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog1 = "swap4.coff";
    char *prog2 = "swap4.coff";
    char *prog3 = "swap4.coff";

    char *prog4 = "swap5.coff";
    char *prog5 = "swap5.coff";
    char *prog6 = "swap5.coff";

    char *prog7 = "write11.coff";
   
    int pid1, pid2, pid3, pid4, pid5, pid6, pid7;
    int status = 0;
    int r;

    printf ("exec prog1");
    pid1 = exec (prog1, 0, 0);
    printf ("exec prog2");
    pid2 = exec (prog2, 0, 0);
    printf ("exec prog3");
    pid3 = exec (prog3, 0, 0);

    printf ("exec prog4");
    pid4 = exec (prog4, 0, 0);
    printf ("exec prog5");
    pid5 = exec (prog5, 0, 0);
    printf ("exec prog6");
    pid6 = exec (prog6, 0, 0);

    printf ("exec prog7");
    pid7 = exec (prog7, 0, 0);

    printf ("join prog1");
    r = join (pid1, &status);
    printf ("join prog2");
    r = join (pid2, &status);
    printf ("join prog3");
    r = join (pid3, &status);

    printf ("join prog4");
    r = join (pid4, &status);
    printf ("join prog5");
    r = join (pid5, &status);
    printf ("join prog6");
    r = join (pid6, &status);

    printf ("join prog7");
    r = join (pid7, &status);

    // the exit status of this process is the pid of the child process
    exit (pid1);
    exit (pid2);
    exit (pid3);
    exit (pid4);
    exit (pid5);
    exit (pid6);
    exit (pid7);
}