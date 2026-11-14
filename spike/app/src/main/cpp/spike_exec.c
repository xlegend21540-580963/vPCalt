#include <stdio.h>
#include <unistd.h>

int main(void) {
    printf("EXEC_OK pid=%d\n", (int)getpid());
    fflush(stdout);
    return 0;
}
