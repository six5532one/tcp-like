Usage
=====
The Sender sends from port 5555 and listens for ACKs on the user-specified `ack_port_num`.
When testing with newudpl, please configure the source port to be 5555:
$ newudpl -i<senderIP>/5555 -o<receiverIP>:<receiverListeningPort> -v -L<percentToDrop>

Suppose the Sender has IP address 128.59.15.70, is sending all segments from port 5555
and listens for ACKs on port 6000;
the proxy running newudpl has IP address 128.59.15.62 and is listening on port 41192;
and the Receiver has IP address 128.59.15.68 and is listening on port 7000:

// proxy
$ ssh ec2805@128.59.15.62
$ ./newudpl -i128.59.15.70/5555 -o128.59.15.68:7000 -v -L60

// Sender
$ ssh ec2805@128.59.15.70
$ make clean
$ make
$ java Sender infile 128.59.15.62 41192 6000 sendLog

// Receiver
$ ssh ec2805@128.59.15.68
$ make clean
$ make
$ java Receiver outfile 7000 128.59.15.70 6000 receiveLog
$ cat outfile

Features
========
Implemented fast retransmit.
