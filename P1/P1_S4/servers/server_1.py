import socket
import json

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(('0.0.0.0', 4665))    # Puerto que se encuentre abierto

print("GPS Sniffer - Port 4665\n")
            
try:
    while True:
        data, addr = sock.recvfrom(4096)
        
        try:
            gps = json.loads(data.decode('utf-8'))
            
            # Display formatted data
            print(f"  📍 Latitude:  {gps['latitude']}")
            print(f"  📍 Longitude: {gps['longitude']}")
            print(f"  📍 Altitude:  {gps['altitude']} m")
            print(f"  📍 Timestamp: {gps['timestamp']}")
            print("-" * 50)
            
        except:
            print(f"Error parsing: {data}")
    
except KeyboardInterrupt:
    sock.close()
       