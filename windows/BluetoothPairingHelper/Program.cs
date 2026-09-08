using Windows.Devices.Bluetooth;
using Windows.Devices.Enumeration;

string wantedName = args.Length > 0 ? args[0] : "Galaxy A05s";
string selector = BluetoothDevice.GetDeviceSelectorFromPairingState(false);
DeviceInformationCollection devices = await DeviceInformation.FindAllAsync(selector);
DeviceInformation? target = devices.FirstOrDefault(device =>
    string.Equals(device.Name, wantedName, StringComparison.OrdinalIgnoreCase));

if (target is null)
{
    Console.Error.WriteLine($"{wantedName} was not found. Make the phone visible and retry.");
    return 2;
}

if (target.Pairing.IsPaired)
{
    Console.WriteLine($"{wantedName} is already paired.");
    return 0;
}

DeviceInformationCustomPairing pairing = target.Pairing.Custom;
pairing.PairingRequested += (_, request) =>
{
    Console.WriteLine($"Pairing request: {request.PairingKind}" +
                      (string.IsNullOrEmpty(request.Pin) ? "" : $"; PIN {request.Pin}"));
    if (request.PairingKind == DevicePairingKinds.ProvidePin)
        request.Accept("0000");
    else
        request.Accept();
};

DevicePairingKinds acceptedKinds =
    DevicePairingKinds.ConfirmOnly |
    DevicePairingKinds.DisplayPin |
    DevicePairingKinds.ProvidePin |
    DevicePairingKinds.ConfirmPinMatch;

DevicePairingResult result = await pairing.PairAsync(
    acceptedKinds, DevicePairingProtectionLevel.None);
Console.WriteLine($"Pairing result: {result.Status}");
return result.Status is DevicePairingResultStatus.Paired or DevicePairingResultStatus.AlreadyPaired ? 0 : 1;
