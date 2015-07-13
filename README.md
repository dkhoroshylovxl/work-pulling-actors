# Work Pulling Actors

Work pulling with [Akka actors](http://akka.io/). A balanced, workload routing solution.

There's a master that will receive work units and delegates them to its workers. Work is forwarded to workers
only when they are finished with their previous job and request a new one. Until then, work units received in
the master are kept in a collection. That is pluggable, you decide what kind of collection,
it just has to fulfill a small contract, the `WorkBuffer` trait defined. If it's bounded
and excessive work is coming, the master won't accept it but reply with a message saying it is busy.

* To define the work that the workers must do, just implement the function `doWork`.
* Results are sent back wrapped in Try objects, so both Success and Failure are permitted.
* Results are sent back paired with the original work units so that they could be identified and matched if necessary.
* Even though Failures should be properly built and handled, if a worker happens to crash, it will be discarded
and replaced by a new worker, ignoring the problematic work unit.

## Example

```
git clone https://github.com/kovacshuni/work-pulling-actors.git
cd work-pulling-actors/example
sbt run
```

`libraryDependencies += "com.hunorkovacs" %% "work-pulling-actors" % "1.0.0-SNAPSHOT"`

Define what working means by extending worker:

```scala
class MyWorker extends Worker[String, Int] {

  import context.dispatcher

  override protected def doWork(s: String) = Future(s.toInt)
}
```

Define how to create worker in master:

```scala
class MyMaster(nWorkers: Int, workBuffer: WorkBuffer[String]) extends Master[String, Int](nWorkers, workBuffer) {

  override protected def newWorkerProps = Props(classOf[MyWorker])
}
```

Run it:

```scala
val master = sys.actorOf(Props(classOf[MyMaster], 3, queue))
val inbox = Inbox.create(sys)

(1 to n) foreach { i =>
  inbox.send(master, WorkFrom[String](i.toString))
}

val sum = (1 to n).foldLeft(0) { (acc, _) =>
  acc + inbox.receive(2 seconds).asInstanceOf[Result[String, Int]].result.get
}
```

## Contributing

Just create a pull-request, we'll discuss it, i'll try to be quick.

## Owner

Hunor Kovács
kovacshuni@yahoo.com
[hunorkovacs.com](http://www.hunorkovacs.com)

This solution was based off of Michael Pollmeier's [akka-patterns](https://github.com/mpollmeier/akka-patterns),
but it was changed a lot.

## Licence

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) .
