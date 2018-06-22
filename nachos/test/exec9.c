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
    int pid1, pid2, pid3, pid4, pid5, pid6;
    int status = 0;
    int r;

    char *prog2 = "swap5.coff";

    printf ("exec prog1");
    pid1 = exec (prog1, 0, 0);
    pid3 = exec (prog1, 0, 0);
    pid5 = exec (prog1, 0, 0);
    printf ("exec prog2");
    pid2 = exec (prog2, 0, 0);
    pid4 = exec (prog2, 0, 0);
    pid6 = exec (prog2, 0, 0);

    // the exit status of this process is the pid of the child process
    exit (pid1);
    exit (pid2);
    exit (pid3);
    exit (pid4);
    exit (pid5);
    exit (pid6);
}
