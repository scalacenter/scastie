# This `Vagrantfile` is used to test/run sbt deploy task on development machine
# using deployment/production.conf to ensure everything is ok before deploying to production env.
# Steps:
# 0. Install vagrant and virtualbox
# 1. `vagrant up` in this `scastie` directory to create & start 2 ubuntu VMs
#    which will be used to run sbt-runners and server (and mongodb)
# 2. You should (optional) use static LAN IP. In this guide, we use `192.168.86.147`
# 3. Run [docker registry](https://docs.docker.com/registry/) locally
#    `docker run -d -p 5000:5000 --name registry registry:2`
# 4. Add `"insecure-registries": ["192.168.86.147:5000"]` into your
#   [docker daemon config](https://docs.docker.com/config/daemon/#configure-the-docker-daemon)
#   Then restart your docker daemon
# 5. Change `ImageName` in `docker / imageNames` in project/DockerHelper.scala, add:
#   registry = Some("192.168.86.147:5000")
# 6. Add to your ~/.ssh/config:
# Host scastie-sbt.scala-lang.org
#    HostName 192.168.33.10
#    User scastie
#    IdentityFile ~/.vagrant.d/insecure_private_key
#    CheckHostIP no
#    StrictHostKeyChecking no
#    PasswordAuthentication no
#    IdentitiesOnly yes
#    UserKnownHostsFile /dev/null
# Host scastie.scala-lang.org
#    HostName 192.168.33.12
#    ... same as above
#
# Note: 192.168.33.10, 192.168.33.12 are IPs of `runner`, `server` VMs as defined bellow.
#
# 7. Confirm that you can ssh to the VMs (and not the actual production servers):
# ```
# ssh scastie.scala-lang.org 'ip -4 -brief addr | grep 192.168'
# ssh scastie-sbt.scala-lang.org 'ip -4 -brief addr | grep 192.168'
# ```
# The output must not empty and contains `192.168.33.10`, `192.168.33.12`
#
# 8. Change deployment/production.conf: sbt-runners.ports-size = 2
# 9. If you don't have access to `github.com/scalacenter/scastie-secrets`:
#    Change `secretsFile` to `SecretsFile.local(..)` in
#    deployRunnersQuick, deployServerQuick taskDefs in project/Deployment.scala
# 10. Run `sbt deploy` to deploy scastie to VMs
#    To speedup the redeployment process, you can see:
#    + The `deploy` command alias defined in build.sbt
#    + The comment about `addInstructions` in project/DockerHelper.runnerDockerfile
# 11. You should revert step 6, 8 after done testing/ deploying with vagrant
#
Vagrant.configure("2") do |config|
  # config.ssh.username = "scastie"
  config.vm.box = "ubuntu/focal64"

  # disable box_check_update and synced_folder to speed up
  config.vm.box_check_update = false
  config.vm.synced_folder ".", "/vagrant", disabled: true
  config.ssh.insert_key = false

  config.vm.provision "shell", inline: <<-SHELL
    # Install docker
    apt-get update
    apt-get install -y apt-transport-https curl
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    echo \
      "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu \
      $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io

    # Add user `scastie` with group `scastie` and supplementary groups `docker`
    groupadd -g 433 scastie
    useradd scastie --uid 433 --gid 433 --create-home --shell /bin/bash --groups docker

    # Setup ssh and sudo for user `scastie`
    cp -pr /home/vagrant/.ssh /home/scastie/
    chown -R scastie:scastie /home/scastie/.ssh
    echo "%scastie ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/scastie

    # add my own registry
    echo '{ "insecure-registries": ["192.168.86.147:5000"] }' > /etc/docker/daemon.json
    service docker restart

    echo "192.168.33.10 scastie-sbt.scala-lang.org" >> /etc/hosts
    echo "192.168.33.12 scastie.scala-lang.org" >> /etc/hosts
  SHELL

  config.vm.define "runner" do |c|
    c.vm.network "private_network", ip: "192.168.33.10"
    c.vm.hostname = "runner"
    c.vm.provider "virtualbox" do |vb|
      vb.cpus = 2
      vb.memory = "2560"
    end
  end

  config.vm.define "server" do |c|
    c.vm.network "private_network", ip: "192.168.33.12"
    c.vm.hostname = "server"
    c.vm.provider "virtualbox" do |vb|
      vb.cpus = 2
      vb.memory = "2560"
    end

    # upload to /home/vagrant/.ssh/
    c.vm.provision "file",
      source: "~/.vagrant.d/insecure_private_key",
      destination: "~/.ssh/insecure_private_key"

    c.vm.provision "shell", inline: <<-SHELL
      # config ssh so scastie on server can ssh runner
      echo "Host scastie-sbt.scala-lang.org
         IdentityFile ~/.ssh/insecure_private_key
         CheckHostIP no
         StrictHostKeyChecking no
         PasswordAuthentication no
         IdentitiesOnly yes
         UserKnownHostsFile /dev/null" > /home/scastie/.ssh/config
      chmod 600 /home/scastie/.ssh/config

      install -m 600 \
        /home/vagrant/.ssh/insecure_private_key \
        /home/scastie/.ssh/insecure_private_key
      chown -R scastie:scastie /home/scastie/.ssh

      # run mongodb on `server`
      docker run -d --restart=always --name mongo --network=host -v /opt/mongo_data:/data/db mongo
    SHELL
  end
end
