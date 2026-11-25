#!/system/bin/sh
# Only the SELinux rule is removed with this module. The hook's own state (hide choices, names, icons) lives in
# /data/system/fadalias and is deliberately kept, so removing this module never wipes the user's configuration.
exit 0
