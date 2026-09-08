param(
    [string]$DeviceName = 'Galaxy A05s'
)

$ErrorActionPreference = 'Stop'
$helperProject = Join-Path $PSScriptRoot 'BluetoothPairingHelper\BluetoothPairingHelper.csproj'

if (Test-Path -LiteralPath $helperProject) {
    dotnet run --project $helperProject --configuration Release -- $DeviceName
    exit $LASTEXITCODE
}

Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;

public static class LocalBluetoothPairing
{
    [StructLayout(LayoutKind.Sequential)]
    private struct FindRadioParams { public int Size; }

    [StructLayout(LayoutKind.Sequential)]
    private struct DeviceSearchParams
    {
        public int Size;
        [MarshalAs(UnmanagedType.Bool)] public bool ReturnAuthenticated;
        [MarshalAs(UnmanagedType.Bool)] public bool ReturnRemembered;
        [MarshalAs(UnmanagedType.Bool)] public bool ReturnUnknown;
        [MarshalAs(UnmanagedType.Bool)] public bool ReturnConnected;
        [MarshalAs(UnmanagedType.Bool)] public bool IssueInquiry;
        public byte TimeoutMultiplier;
        public IntPtr Radio;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct SystemTime
    {
        public ushort Year, Month, DayOfWeek, Day, Hour, Minute, Second, Milliseconds;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct DeviceInfo
    {
        public int Size;
        public ulong Address;
        public uint ClassOfDevice;
        [MarshalAs(UnmanagedType.Bool)] public bool Connected;
        [MarshalAs(UnmanagedType.Bool)] public bool Remembered;
        [MarshalAs(UnmanagedType.Bool)] public bool Authenticated;
        public SystemTime LastSeen;
        public SystemTime LastUsed;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 248)] public string Name;
    }

    [DllImport("bthprops.cpl", SetLastError = true)]
    private static extern IntPtr BluetoothFindFirstRadio(ref FindRadioParams parameters, out IntPtr radio);
    [DllImport("bthprops.cpl")]
    private static extern bool BluetoothFindNextRadio(IntPtr find, out IntPtr radio);
    [DllImport("bthprops.cpl")]
    private static extern bool BluetoothFindRadioClose(IntPtr find);
    [DllImport("bthprops.cpl", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr BluetoothFindFirstDevice(ref DeviceSearchParams parameters, ref DeviceInfo info);
    [DllImport("bthprops.cpl", CharSet = CharSet.Unicode)]
    private static extern bool BluetoothFindNextDevice(IntPtr find, ref DeviceInfo info);
    [DllImport("bthprops.cpl")]
    private static extern bool BluetoothFindDeviceClose(IntPtr find);
    [DllImport("bthprops.cpl", CharSet = CharSet.Unicode)]
    private static extern uint BluetoothAuthenticateDeviceEx(
        IntPtr parentWindow, IntPtr radio, ref DeviceInfo info, IntPtr outOfBandData,
        int authenticationRequirement);
    [DllImport("kernel32.dll")]
    private static extern bool CloseHandle(IntPtr handle);

    public static string PairByName(string wantedName)
    {
        var radioParameters = new FindRadioParams { Size = Marshal.SizeOf(typeof(FindRadioParams)) };
        IntPtr radio;
        IntPtr radioFind = BluetoothFindFirstRadio(ref radioParameters, out radio);
        if (radioFind == IntPtr.Zero)
            return "No usable Windows Bluetooth radio was found (error " + Marshal.GetLastWin32Error() + ").";

        try
        {
            do
            {
                var search = new DeviceSearchParams {
                    Size = Marshal.SizeOf(typeof(DeviceSearchParams)),
                    ReturnAuthenticated = true, ReturnRemembered = true,
                    ReturnUnknown = true, ReturnConnected = true,
                    IssueInquiry = true, TimeoutMultiplier = 8, Radio = radio
                };
                var device = new DeviceInfo { Size = Marshal.SizeOf(typeof(DeviceInfo)) };
                IntPtr deviceFind = BluetoothFindFirstDevice(ref search, ref device);
                if (deviceFind != IntPtr.Zero)
                {
                    try
                    {
                        do
                        {
                            if (string.Equals(device.Name, wantedName, StringComparison.OrdinalIgnoreCase))
                            {
                                if (device.Authenticated)
                                    return wantedName + " is already paired.";
                                uint result = BluetoothAuthenticateDeviceEx(
                                    IntPtr.Zero, radio, ref device, IntPtr.Zero, 0);
                                return result == 0
                                    ? wantedName + " paired successfully."
                                    : "Pairing returned Windows error " + result + ".";
                            }
                            device = new DeviceInfo { Size = Marshal.SizeOf(typeof(DeviceInfo)) };
                        } while (BluetoothFindNextDevice(deviceFind, ref device));
                    }
                    finally { BluetoothFindDeviceClose(deviceFind); }
                }
                CloseHandle(radio);
            } while (BluetoothFindNextRadio(radioFind, out radio));
        }
        finally { BluetoothFindRadioClose(radioFind); }

        return "The device was not found. Make it visible in A05s Input and try again.";
    }
}
'@

[LocalBluetoothPairing]::PairByName($DeviceName)
