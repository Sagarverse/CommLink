su -c '
setprop sys.usb.config none
sleep 1
base=/config/usb_gadget/g_hid
mkdir -p $base
echo 0x1d6b > $base/idVendor
echo 0x0104 > $base/idProduct
mkdir -p $base/strings/0x409
echo "CommLink" > $base/strings/0x409/manufacturer
echo "CommLink USB HID" > $base/strings/0x409/product
mkdir -p $base/configs/c.1/strings/0x409
echo "HID" > $base/configs/c.1/strings/0x409/configuration
mkdir -p $base/functions/hid.usb0
echo 1 > $base/functions/hid.usb0/protocol
echo 1 > $base/functions/hid.usb0/subclass
echo 8 > $base/functions/hid.usb0/report_length
printf "%b" "\x05\x01\x09\x06\xa1\x01\x05\x07\x19\xe0\x29\xe7\x15\x00\x25\x01\x75\x01\x95\x08\x81\x02\x95\x01\x75\x08\x81\x03\x95\x05\x75\x01\x05\x08\x19\x01\x29\x05\x91\x02\x95\x01\x75\x03\x91\x03\x95\x06\x75\x08\x15\x00\x25\x65\x05\x07\x19\x00\x29\x65\x81\x00\xc0" > $base/functions/hid.usb0/report_desc
ln -s $base/functions/hid.usb0 $base/configs/c.1/
udc=$(ls -1 /sys/class/udc/)
echo "$udc" > $base/UDC
'
