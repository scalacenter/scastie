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

    rhc app create -a play2demo -t diy-0.1 --nogit -l yourlogin

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

And then add this repository as a remote repo named quickstart:

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

To deploy your changes, you can just repeat the steps from play stage, or use the helper script 'openshift_deploy'.

Working with a mysql database
----------------------------

Just issue:

    rhc app cartridge add -a play2scala -c mysql-5.1

Don't forget to write down the credentials.

Then uncomment the following lines from your conf/openshift.conf, like this:

    # openshift mysql database
    db.default.driver=com.mysql.jdbc.Driver
    db.default.url="jdbc:mysql://"${OPENSHIFT_DB_HOST}":"${OPENSHIFT_DB_PORT}/${OPENSHIFT_APP_NAME}
    db.default.user=${OPENSHIFT_DB_USERNAME}
    db.default.password=${OPENSHIFT_DB_PASSWORD}

You'll also have to include the mysql driver as a dependency. Add this line to project/Build.scala file:

    val appDependencies = Seq( 
        "mysql" % "mysql-connector-java" % "5.1.18" 
    ) 

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

A step by step example: deploying computer-database sample app to openshift
-------------------------

You can add openshift support to an already existing play application. 

Let's take the computer-database sample application.

```bash
    cd PLAY_INSTALL_FOLDER/samples/scala/computer-database

    git init
    rhc app create -a computerdb -t diy-0.1 --nogit
```

We add the "--nogit" parameter to tell openshift to create the remote repo but don't pull it locally. You'll see something like this:

```bash
    Confirming application 'computerdb' is available:  Success!

    computerdb published:  http://computerdb-yournamespace.rhcloud.com/
    git url:  ssh://uuid@computerdb-yournamespace.rhcloud.com/~/git/computerdb.git/
```
Copy and paste the git url to add it as a remote repo (replace the uuid part with your own!)

    git remote add origin ssh://uuid@computerdb-yourdomain.rhcloud.com/~/git/computerdb.git/
    git pull -s recursive -X theirs origin master
    git add .
    git commit -m "initial deploy"

That's it, you have just cloned your openshift repo, now we will add the quickstart repo:

    git remote add quickstart -m master git://github.com/opensas/play2-openshift-quickstart.git
    git pull -s recursive -X theirs quickstart master

Then run the stage task, add your changes to git's index, commit and push the repo upstream (you can also just run the *openshift_deploy* script):

    play clean compile stage
    git add .
    git commit -m "deploying computerdb application"
    git push origin

To see if the push was successful, open another console and check the logs with the following command:

    rhc app tail -a computerdb

Oops, looks like there's a problem.

```
[warn] play - Run with -DapplyEvolutions.default=true if you want to run them automatically (be careful)
Oops, cannot start the server.
PlayException: Database 'default' needs evolution! [An SQL script need to be run on your database.]
```

On development mode, play will ask you to run pending evolutions to database, but when in prod mode, you have to specify it form the command line. Let's configure play to automatically apply evolutions. Edit the file conf/openshift.conf like this:

```
# openshift action_hooks scripts configuration
# ~~~~~
openshift.play.params="-DapplyEvolutions.default=true"
```

Now deploy your app once again with './openshift_deploy -q'

That's it, you can now see computerdb demo application running at:

    http://computerdb-yournamespace.rhcloud.com

But there's one more thing you could do. Right now, your application is using the h2 in memory database that comes bundled with play. Openshift may decide to swap your application out of memory if it detects there's no activity. So you'd better persist the information somewhere. That's easy, just edit your count/openshift.conf file to tell play to use a file database whenever it's running on openshift, like this:

```
db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:"${OPENSHIFT_DATA_DIR}db/computerdb
```

Now, if you feel brave, you may port it to mysql. Add the mysql cartridge to you openshift application:

```
rhc app cartridge add -a computerdb -c mysql-5.1
```

There are a couple of differences you'll have to handle. Have a look at this quickstart to see what needs to be changed: https://github.com/opensas/openshift-play2-computerdb

I'll give you a few tips: the sample app uses H2 sequences instead of mysql auto_increment fields; you'll also have to modify the computer.insert method not to pass the id field; in order for the referential integrity to work you'll have to create the tables using the innodb engine; and you'll have to replace 'SET REFERENTIAL_INTEGRITY FALSE | TRUE' command with 'SET FOREIGN_KEY_CHECKS = 0 | 1;'.

Then edit you conf/openshift.conf file like this

    # openshift mysql database
    db.default.driver=com.mysql.jdbc.Driver
    db.default.url="jdbc:mysql://"${OPENSHIFT_DB_HOST}":"${OPENSHIFT_DB_PORT}/${OPENSHIFT_APP_NAME}
    db.default.user=${OPENSHIFT_DB_USERNAME}
    db.default.password=${OPENSHIFT_DB_PASSWORD}

You'll also have to include the mysql driver as a dependency. Add this line to project/Build.scala file:

    val appDependencies = Seq( 
        "mysql" % "mysql-connector-java" % "5.1.18" 
    ) 

You can manage your new MySQL database by embedding phpmyadmin-3.4.

    rhc app cartridge add -a computerdb -c phpmyadmin-3.4

Deploy once again, and you'll have your computerdb application running on openshift with mysql at:

    http://computerdb-yournamespace.rhcloud.com

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