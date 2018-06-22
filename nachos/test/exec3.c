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
    char *prog1 = "write10.coff";
    char *prog2 = "write10.coff";
    char *prog3 = "write10.coff";
    int pid1, pid2, pid3;
    int status = 0;
    int r;

    printf ("exec prog1");
    pid1 = exec (prog1, 0, 0);
    printf ("exec prog2");
    pid2 = exec (prog2, 0, 0);
    printf ("exec prog3");
    pid3 = exec (prog3, 0, 0);
    printf ("join prog1");
    r = join (pid1, &status);
    printf ("join prog2");
    r = join (pid2, &status);
    printf ("join prog3");
    r = join (pid3, &status);

    // the exit status of this process is the pid of the child process
    exit (pid1);
    exit (pid2);
    exit (pid3);
}
