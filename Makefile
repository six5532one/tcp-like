JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
		  Sender.java \
		  Receiver.java \
		  AckListener.java \
		  Logger.java \
		  Timer.java \
		  BitWrangler.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
