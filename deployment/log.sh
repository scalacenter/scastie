names=$(docker ps --format "{{.Names}}")
echo "tailing $names"

while read -r name
do
  # eval to show container name in jobs list
  eval "docker logs -f --tail=5 \"$name\" | sed -e \"s/^/[-- $name --] /\" &"
done <<< "$names"

function _exit {
  echo
  echo "Stopping tails $(jobs -p | tr '\n' ' ')"
  echo "..."

  # Using `sh -c` so that if some have exited, that error will
  # not prevent further tails from being killed.
  jobs -p | tr '\n' ' ' | xargs -I % sh -c "kill % || true"

  echo "Done"
}

# On ctrl+c, kill all tails started by this script.
trap _exit EXIT

# Don't exit this script until ctrl+c or all tails exit.
wait
