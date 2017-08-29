# Maximal-Causality-Reduction (MCR) Java version

MCR is a stateless model checker wrapped around an efficient reduction algorithm. It works on the trace of a program and constructs ordering constraints over the trace to generate other possible schedules. It takes the values of the writes and reads to prune redundant executions. By enforcing at least one read to return a different value, it generates a new schedule which drives the program to reach a new state. 

# Design of MCR

The current version of MCR is implemented in Java using [ASM](http://asm.ow2.org/) and [Z3](https://github.com/Z3Prover/z3). The workflow of MCR is as follows.

<div style="text-align:center"><img src="figs/mcr.png" alt="Drawing" style="width: 220px;" /></div>

It contains three major steps:

* collecting trace (`mcr-controller`)
* constructing constraints over the trace (`mcr-engine`)
* building new interleavings (a sequence of thread schedules) from the solution to the constraints (`mcr-engine`)

## Trace collection

To collect the trace of a target program, it first uses ASM to instrument the bytecode of the program. Then it executes the instrumented class under a dynamic scheduler and log the corresponding events. The logged events include accesses to shared memory locations, synchronizations (lock/unlock, wait/notify, etc.) and thread create/join. The rewrite of the bytecode is under `mcr-controller/instr-explr-src/edu.tamu.aser.rvinstrumentor/`. The scheduler implementation is under `mcr-controller/instr-explr-src/edu.tamu.aser.exploration/`.

## Constraints Construction

After collecting the trace, MCR leverages the [Maximal Causality Model](http://fsl.cs.illinois.edu/FSL/papers/2014/huang-meredith-rosu-2014-pldi/huang-meredith-rosu-2014-pldi-public.pdf) to construct ordering constraints over the trace to compute all the possible interleavings that can be derived from the considered trace. For each event in the trace, MCR introduces an integer variable *O* to represent its order in the new interleaving. *E.g.,* if *e1* should happen before *e2* in the new interleaing, then their ordering is encoded as *O1<O2*. The construction of the constraint model is under the folder aser-engine/src/main/...


## Interleaving Generation
MCR uses the Z3 SMT solver to solve the constraints. If there exists a solution to the constraints, MCR can compute a sequence of events of which the ordering corresponds to the value of its corresponding variable *O*. Then MCR maps the sequence of events to the sequence of the corresponding thread ID as the seed interleaving. This is implemented under `aser-engine/src/main/`.


# Getting Started

## Environment
* z3 installation
	* Follow https://github.com/Z3Prover/z3
* Eclipse (Neon 4.6) & Java 8
	* The JDK version tested is 8u101. Higher versions should also be OK. JDK 8u65 was also tested, but it reported an exception related to the lamdba feature. We suggest the users to use JDK 8u101 or higher versions.

## How to Run
### run in Eclipse
The tool can be easily used in Eclipse. Create an Eclispe workspace and import all the projects cloned from the Git repository (A build path error may happen. It may need to manually change the JDK version if the error happens). We put all the benchmarks under the folder `mcr-test/`. Users can choose a benchmark and then run it as a junit test. We use java agent for the bytecode instrumentation with the ASM framework. Users need to specify the following VM parameters (click the Run Configurations and choose the JUnit):

```
-Xmx1g -javaagent:lib/iagent.jar 
```

### run in terminal

We also support building the project in the terminal (or in Eclipse) using `ant`. Users can execute `ant ` under `mcr-controller/`, which re-compiles the source code and generates jar files into `../dist` directory. To run the tests, just run the bash script `mcr_cmd` under `mcr-test/`. 
The usages:

```
usage: ./mcr_cmd [options] test_class [parameters]
  e.g., ./mcr_cmd [options] edu.tamu.aser.rvtest_simple_tests.Example
  options:
  	--help -h usage
	--debug -D print debug information
	--static -S using static analysis       (see the ECOOP'17 paper)
	--class_path absolute_dir -c absolute_dir
					if this is not specified, default the current bin/ as the class path
	--memmory-model MM -m MM selecting the memory model SC/TSO/PSO  (see the OOPLSA'16 paper)
	
```

If `-S` is specified, the tool will first run static analysis to generate the system dependencies graph into `SDGs` and then explore the program. The number of the reads and constraitns as well as the constraints solving times are save to the file under `statistical result/`.

`-m` selects the executed memory model. We currently support SC/TSO/PSO, and use SC. 

### to run the users' own tests

MCR works with JUnit 4. Given a JUnit 4 test class, it will explore
each of the tests in the test class. To use MCR, you need to add the
following annotation "@RunWith(JUnit4MCRRunner.class)" to the
test class. Users can refer to the benchmarks we put under 'mcr-test/src' 
for more ideas about how to write the tests.

## An Example

The following shows a simple example (See `Example1.java` under `mcr-test/src/edu.tamu.aser.rvtest_simple_tests/`).


```
package edu.tamu.aser.rvtest;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import edu.tamu.aser.exploration.JUnit4ReExRunner;

@RunWith(JUnit4MCRRunner.class)
public class Example {

	private static int x, y;
	public static void main(String[] args) {
		Thread t1 = new Thread(new Runnable() {
			@Override
			public void run() {
				int b = x;
				y = 1;
			}
		});

		t1.start();
		int a = y;
		x = 1;
		t1.join();
		
	}

	@Test
	public void test() {
		x = 0;
		y = 0;
	}
}
```

After running MCR on the program above, we can generate the following output:

```
EXPLORING: edu.tamu.aser.rvtest_collection.Example.test: 09:12:12


=============== EXPLORATION STATS ===============
NUMBER OF SCHEDULES: 3
EXPLORATION TIME: 0:00:00
=================================================

```

### Explanation of the output


`=============== EXPLORATION STATS ===============`

shows the number of the executions explored for this progam and the time taken.

When an exception is triggered by the program, the tool will print how this error is triggered and how to reproduce it. Take `TestDeadlock.java` under `mcr-test/src/edu.tamu.aser.rvtest_simple_tests/` as an example. Our tool prints out
the buggy trace:

```
!!! FAILURE DETECTED DURING EXPLORATION OF SCHEDULE #2: Deadlock detected in schedule
The following trace triggered this error:
       Thread-1_TestDeadlock.java:67:start
       Thread-1_TestDeadlock.java:68:start
       Thread-6_TestDeadlock.java:63:read
       Thread-6_TestDeadlock.java:29:read
       Thread-6_TestDeadlock.java:29:Lock
       Thread-6_TestDeadlock.java:35:read
       Thread-5_TestDeadlock.java:55:read
       Thread-5_TestDeadlock.java:19:read
       Thread-5_TestDeadlock.java:19:Lock
       Thread-5_TestDeadlock.java:20:write
       Thread-5_TestDeadlock.java:21:read
       Thread-6_TestDeadlock.java:36:read
```



# Useful Documents
* [ECOOP'17] [Speeding Up Maximal Causality Reduction with Static Dependency Analysis](https://huangshiyou.github.io/files/Huang-ECOOP-2017-16.pdf)`

* [OOPSLA'16] [Maximal Causality Reduction for TSO and PSO](https://huangshiyou.github.io/files/mcr_relax-huang.pdf)

* [PLDI'15] [Stateless Model Checking Concurrent Programs with Maximal Causality Reduction](https://parasol.tamu.edu/~jeff/academic/mcr.pdf)

* [PLDI'14] [Maximal Sound Predictive Race Detection
with Control Flow Abstraction](http://fsl.cs.illinois.edu/FSL/papers/2014/huang-meredith-rosu-2014-pldi/huang-meredith-rosu-2014-pldi-public.pdf)
