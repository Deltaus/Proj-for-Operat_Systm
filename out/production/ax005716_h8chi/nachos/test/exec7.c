/*
 * exec1.c
 *
 * Simple program for testing exec.  It does not pass any arguments to
 * the child.
 */

#include "syscall.h"

int
main (int argc, char *argv[])
{
    char *prog1 = "swap4.coff";
    int pid1, pid2;
    int status = 0;
    int r;

    char *prog2 = "swap5.coff";

    printf ("exec prog1");
    pid1 = exec (prog1, 0, 0);
    printf ("exec prog2");
    pid2 = exec (prog2, 0, 0);
    printf ("join prog1");
    r = join (pid1, &status);
    printf ("join prog2");
    r = join (pid2, &status);

    // the exit status of this process is the pid of the child process
    exit (pid1);
    exit (pid2);
}
