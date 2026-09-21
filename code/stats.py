import re
import sys

if len(sys.argv) < 2:
    print("Uso: python3 stats.py <caminho_do_log>")
    sys.exit(1)

log_file = sys.argv[1]

# Inicializar contadores
update_numbers = set()
update_repeats = 0
total_updates_received = 0

file_part_numbers = set()
file_part_repeats = 0
total_file_parts_received = 0

acks_update = 0
acks_file_part = 0

# Expressões regulares
update_pattern = re.compile(r"Novo UPDATE recebido: UPDATE;\d+;(\d+);\d+")
update_repeat_pattern = re.compile(r"UPDATE repetido \((\d+)\)")
file_part_pattern = re.compile(r"FILE_PART nova recebida: parte (\d+)")
file_part_repeat_pattern = re.compile(r"FILE_PART repetido \((\d+)\)")
ack_update_pattern = re.compile(r"ACK enviado.*\(UPDATE")
ack_file_part_pattern = re.compile(r"ACK enviado.*\(FILE_PART")

with open(log_file, "r") as f:
    for line in f:
        # ACKs
        if ack_update_pattern.search(line):
            acks_update += 1
        elif ack_file_part_pattern.search(line):
            acks_file_part += 1

        # Updates
        m = update_pattern.search(line)
        if m:
            total_updates_received += 1
            update_numbers.add(int(m.group(1)))
        if update_repeat_pattern.search(line):
            update_repeats += 1
            total_updates_received += 1  # contar repetido também

        # File parts
        m_fp = file_part_pattern.search(line)
        if m_fp:
            total_file_parts_received += 1
            file_part_numbers.add(int(m_fp.group(1)))
        if file_part_repeat_pattern.search(line):
            file_part_repeats += 1
            total_file_parts_received += 1  # contar repetido também

# Estatísticas UPDATE
last_update = max(update_numbers) if update_numbers else 0
lost_updates = set(range(1, last_update + 1)) - update_numbers
unique_updates = total_updates_received - update_repeats

# Estatísticas FILE_PART
last_file_part = max(file_part_numbers) if file_part_numbers else 0
lost_file_parts = set(range(0, last_file_part + 1)) - file_part_numbers
unique_file_parts = total_file_parts_received - file_part_repeats

# Resultados
print("===== Estatísticas do Log =====")
print("UPDATES:")
print(f"  Total recebidos: {total_updates_received}")
print(f"  Repetidos: {update_repeats}")
print(f"  Updates únicos: {unique_updates}")
print(f"  Último UPDATE: {last_update}")
print(f"  Perdidos: {len(lost_updates)} -> {sorted(lost_updates)}")
print(f"  ACKs enviados (UPDATE): {acks_update}\n")

print("FILE_PARTS:")
print(f"  Total recebidos: {total_file_parts_received}")
print(f"  Repetidos: {file_part_repeats}")
print(f"  FILE_PART únicos: {unique_file_parts}")
print(f"  Último FILE_PART: {last_file_part}")
print(f"  Perdidos: {len(lost_file_parts)} -> {sorted(lost_file_parts)}")
print(f"  ACKs enviados (FILE_PART): {acks_file_part}")
