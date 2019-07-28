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
    int pid;
    char *prog = "exit1.coff";
    pid = exec (prog, 0, 0);
    // the exit status of this process is the pid of the child process
    exit(pid);

}
