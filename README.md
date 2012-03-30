Play framework 2 application on OpenShift Express
============================

This git repository will help you get up and running quickly with a Play framework 2 application
on OpenShift Express taking advantage of the do-it-yourself cartridge.


Running on OpenShift
--------------------

Create a new Play framework 2 application

    play new play2demo
    cd play2demo

    git init

Register at http://openshift.redhat.com/, and then create a diy (do-it-yourself) application:

    rhc app create -a play2demo -t diy-0.1 --nogit

You will see something like the following:

```bash
    Confirming application 'play2demo' is available:  Success!

    play2demo published:  http://play2demo-opensas.rhcloud.com/
    git url:  ssh://uuid@play2demo-yourdomain.rhcloud.com/~/git/play2demo.git/
```

Copy and paste the git url to add it as a remote repo (replace the uuid part with your own!)

    git remote add origin ssh://uuid@play2demo-yourdomain.rhcloud.com/~/git/play2demo.git/
    git pull -s recursive -X theirs origin master
    git add .
    git commit -m "initial deploy"

And then this repository as quickstart:

    git remote add quickstart -m master git://github.com/opensas/play2-openshift-quickstart.git
    git pull -s recursive -X theirs quickstart master

Then use the stage task to prepare your deployment

    play clean compile stage

And add your changes to git's index, commit and push the repo upstream:

    git add .
    git commit -m "a nice message"
    git push origin

That's it, you can now see your application running at:

    http://play2scala-yournamespace.rhcloud.com

The first time you do it, it will take quite a few minutes to complete, because git has to upload play's dependencies, but after that git is smart enough to just upload the differences.

Working with a mysql database
----------------------------

Just issue:

    rhc app cartridge add -a play2scala -c mysql-5.1

Don't forget to write down the credentials.

Then uncomment the following lines from your conf/application.conf, like this:

    # openshift mysql database
    %openshift.db.url=jdbc:mysql://${OPENSHIFT_DB_HOST}:${OPENSHIFT_DB_PORT}/${OPENSHIFT_APP_NAME}
    %openshift.db.driver=com.mysql.jdbc.Driver
    %openshift.db.user=admin
    %openshift.db.pass=<write your password here>

You can manage your new MySQL database by embedding phpmyadmin-3.4.

    rhc app cartridge add -a play2scala -c phpmyadmin-3.4

It's also a good idea to create a different user with limited privileges on the database.

Updating your application
-------------------------

To deploy your changes to openshift just run the stage task, add your changes to the index, commit and push:

```bash
    play clean compile stage
    git add . -A
    git commit -m "a nice message"
    git push origin
```

If you want to do a quick test, you can skip the "clean compile" stuff and just run "play stage"

All right, I know you are lazy, just like me. So I added a little script to help you with that, just run

```bash
    openshift_deploy "a nice message"
```

You may leave the message empty and it will add something like "deployed on Thu Mar 29 04:07:30 ART 2012", you can also pass a "-q" parameter to skip the "clean compile" option.

A step by step exampe: deploying zentasks sample app to openshift
-------------------------

You can add openshift support to an already existing play application. 

Let's take the zentasks sample application.

```bash
    cd PLAY_INSTALL_FOLDER/samples/scala/zentasks

    git init
    rhc app create -a zentasks -t diy-0.1 --nogit
```

We add the "--nogit" parameter to tell openshift to create the remote repo but don't pull it locally. You'll see something like this:

```bash
    Confirming application 'forms' is available:  Success!

    zentasks published:  http://zentasks-yournamespace.rhcloud.com/
    git url:  ssh://uuid@zentasks-yournamespace.rhcloud.com/~/git/zentasks.git/
```
Copy and paste the git url to add it as a remote repo (replace the uuid part with your own!)

    git remote add origin ssh://uuid@play2demo-yourdomain.rhcloud.com/~/git/play2demo.git/
    git pull -s recursive -X theirs origin master
    git add .
    git commit -m "initial deploy"

That's it, you have just cloned your openshift repo, now we will add the quickstart repo:

    git remote add quickstart -m master ggit://github.com/opensas/play2-openshift-quickstart.git
    git pull -s recursive -X theirs quickstart master

Then tun the stage task, add your changes to git's index, commit and push the repo upstream (you can also just run the *openshift_deploy* script):

    play clean compile stage
    git add .
    git commit -m "deploying zentasks application"
    git push origin

But when you go to http://zentasks-yournamespace.rhcloud.com, you'll see a 503 error message, telling you that your application is not currrently running, let's troubleshoot it.

The first thing you'll have to do, is have a look at the logs, just issue:

```
    rhc app tail -a zentasks --opts '-n 50'
```

The "--opts -n 50" stuff is just to see some more lines.

In the output you'll see something like:

```
[warn] play - Run with -DapplyEvolutions.default=true if you want to run them automatically (be careful)
Oops, cannot start the server.
PlayException: Database 'default' needs evolution! [An SQL script need to be run on your database.]
    at play.api.db.evolutions.EvolutionsPlugin$$anonfun$onStart$1.apply(Evolutions.scala:422)
    at play.api.db.evolutions.EvolutionsPlugin$$anonfun$onStart$1.apply(Evolutions.scala:410)
    at scala.collection.LinearSeqOptimized$class.foreach(LinearSeqOptimized.scala:59)
    
```

So the zentasks project needs to run evolution to set the initial data. Let's add it to the conf/openshift.conf file:

```
    # openshift action_hooks scripts configuration
    # ~~~~~
    openshift.play.params=-DapplyEvolutions.default=true
```

Let's deploy it all againg with *openshift_deploy -q*, but once again our application is not running. Let's check the log:

```
Caused by: com.typesafe.config.ConfigException$Parse: openshift.conf: 45: Invalid number: '-' (if you intended '-' to be part of the value for 'openshift.play.params', try enclosing the value in double quotes, or you may be able to rename the file .properties rather than .conf)
    at com.typesafe.config.impl.Parser$ParseContext.nextToken(Parser.java:178)
```

So it seems like we have to enclose play.params in quotes:

```
    # openshift action_hooks scripts configuration
    # ~~~~~
    openshift.play.params="-DapplyEvolutions.default=true"
```

Let's *openshift_deploy -q" once again, and now everything works as expected. With this short example you learnt how to deploy an existing play 2 application to openshift, and also how to check the logs to troubleshoot it.

That's it, you can now see zentasks demo application running at:

    http://zentasks-yournamespace.rhcloud.com

Configuration
-------------

When running on openshift, the configuration defined with conf/application.conf will be overriden by conf/openshift.conf, that way you can customize the way your play app will be executed while running on openshift.

You can also specify additional parameters to pass to play's executable with the **openshift.play.params** key, like this:

    # play framework command configuration
    # ~~~~~
    #openshift.play.params="-Xmx512M"

Don't forget to enclose each param in quotes.


Trouble shooting
----------------------------

To find out what's going on in openshift, issue

    rhc app tail -a play2demo

If you feel like investigating further, you can

    rhc app show -a play2demo

    Application Info
    ================
    play
        Framework: diy-0.1
        Creation: 2012-03-18T12:39:18-04:00
        UUID: youruuid
        Git URL: ssh://youruuid@play-yournamespace.rhcloud.com/~/git/raw.git/
        Public URL: http://play-yournamespace.rhcloud.com

Then you can connect using ssh like this:

    ssh youruuid@play-yournamespace.rhcloud.com


Having a look under the hood
----------------------------

This projects takes advantage of openshift's do-it-yourself cartridge to run play framework 2 application natively.

Everytime you push changes to openshift, the following actions will take place:

* Openshift will run the **.openshift/action_hooks/stop** script to stop the application, in case it's running.

* Then it wil execute **.openshift/action_hooks/start** to start your application. You can specify additional parameters with openshift.play.params.

```bash
    ${OPENSHIFT_REPO_DIR}target/start $PLAY_PARAMS 
        -Dhttp.port=${OPENSHIFT_INTERNAL_PORT}
        -Dhttp.address=${OPENSHIFT_INTERNAL_IP}
        -Dconfig.resource=openshift.conf
```

Play will then run your app in production mode. The server will listen to ${OPENSHIFT_INTERNAL_PORT} at ${OPENSHIFT_INTERNAL_IP}.

* **.openshift/action_hooks/stop** just tries to kill the RUNNING_PID process, and then checks that no "java" process is running. If it's there, it tries five times to kill it nicely, and then if tries another five times to kill it with -SIGKILL.

Acknowledgments
----------------------------

I couldn't have developed this quickstart without the help of [marekjelen](https://github.com/marekjelen) who answered [my questions on stackoverflow](http://stackoverflow.com/questions/9446275/best-approach-to-integrate-netty-with-openshift) and who also shared his [JRuby quickstart repo](https://github.com/marekjelen/openshift-jruby#readme). (I know, open source rocks!)

It was also of great help Grant Shipley's [article on building a quickstart for openshift](https://www.redhat.com/openshift/community/blogs/how-to-create-an-openshift-github-quick-start-project).

Play framework native support for openshift was a long awaited and pretty popular feature (you are still on time to vote for it [here](https://www.redhat.com/openshift/community/content/native-support-for-play-framework-application)). So it's a great thing that Red Hat engineers came out with this simple and powerful solution, that basically let's you implement any server able to run on a linux box. Kudos to them!!!

Licence
----------------------------
This project is distributed under [Apache 2 licence](http://www.apache.org/licenses/LICENSE-2.0.html). 