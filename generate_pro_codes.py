#!/usr/bin/env python3
"""
Generador de códigos de activación PRO para GuardianOS.

Genera códigos en dos formatos:
1. Simple (matemático): GUAR-XXXX-XXXX-XXXX
2. Firmado (RSA): GUAR-[DATA]-[SIGNATURE]

Uso:
    python3 generate_pro_codes.py --type simple --count 10
    python3 generate_pro_codes.py --type signed --device abc123 --expiry 2026-12-31
"""

import argparse
import base64
import random
import hashlib
import json
from datetime import datetime
from pathlib import Path

def generate_simple_code():
    """
    Genera código simple: GUAR-XXXX-XXXX-XXXX
    donde num3 = (num1 + num2) % 10000
    """
    num1 = random.randint(1000, 9999)
    num2 = random.randint(1000, 9999)
    num3 = (num1 + num2) % 10000
    
    return f"GUAR-{num1:04d}-{num2:04d}-{num3:04d}"

def generate_signed_code(device_id="", expiry_date=None, version="1.0"):
    """
    Genera código firmado con RSA (requiere clave privada).
    Formato: GUAR-[DATA]-[SIGNATURE]
    
    NOTA: Esta es una versión DEMO sin firma real RSA.
    Para producción, usar openssl o cryptography.
    """
    # Generar data
    expiry_timestamp = 0
    if expiry_date:
        expiry_timestamp = int(datetime.strptime(expiry_date, "%Y-%m-%d").timestamp() * 1000)
    
    if not device_id:
        device_id = "".join(random.choices("abcdefghijklmnopqrstuvwxyz", k=16))
    
    data_str = f"{device_id}|{expiry_timestamp}|{version}"
    data_b64 = base64.b64encode(data_str.encode()).decode()
    
    # Firma DEMO (SHA-256 hash, NO ES RSA REAL)
    # En producción, reemplazar con firma RSA usando clave privada
    signature_demo = hashlib.sha256(data_str.encode()).digest()[:16]
    signature_b64 = base64.b64encode(signature_demo).decode()
    
    return f"GUAR-{data_b64}-{signature_b64}", device_id

def main():
    parser = argparse.ArgumentParser(description="Generador de códigos PRO para GuardianOS")
    parser.add_argument("--type", choices=["simple", "signed"], default="simple",
                       help="Tipo de código a generar")
    parser.add_argument("--count", type=int, default=1,
                       help="Cantidad de códigos simples a generar")
    parser.add_argument("--device", type=str, default="",
                       help="Device ID para código firmado (opcional)")
    parser.add_argument("--expiry", type=str, default=None,
                       help="Fecha de expiración (YYYY-MM-DD) para código firmado")
    parser.add_argument("--output", type=str, default="pro_codes.txt",
                       help="Archivo de salida")
    
    args = parser.parse_args()
    
    codes = []
    
    if args.type == "simple":
        print(f"🔑 Generando {args.count} códigos simples...\n")
        for i in range(args.count):
            code = generate_simple_code()
            codes.append(code)
            print(f"  {i+1}. {code}")
    
    elif args.type == "signed":
        print("🔐 Generando código firmado...\n")
        code, device_id = generate_signed_code(args.device, args.expiry, "1.0")
        codes.append(code)
        print(f"  Código: {code}")
        print(f"  Device ID: {device_id}")
        if args.expiry:
            print(f"  Expira: {args.expiry}")
        else:
            print(f"  Expira: Sin vencimiento")
        print("\n⚠️  NOTA: Esta es una firma DEMO (SHA-256). Para producción, usar RSA-2048.")
    
    # Guardar en archivo
    output_path = Path(args.output)
    with open(output_path, "w") as f:
        f.write("# GuardianOS - Códigos de Activación PRO\n")
        f.write(f"# Generados: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"# Tipo: {args.type}\n\n")
        for code in codes:
            f.write(f"{code}\n")
    
    print(f"\n✅ Códigos guardados en: {output_path}")
    print("\n📖 Instrucciones de uso:")
    print("   1. Abre GuardianOS en el dispositivo Android")
    print("   2. Ve a la pantalla de activación PRO")
    print("   3. Ingresa el código generado")
    print("   4. ¡Disfruta de todas las funciones PRO!")

if __name__ == "__main__":
    main()
