import re

with open("/app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()

stack = []
lines = code.split("\n")

line_num = 1
col_num = 1
in_block_comment = False
in_line_comment = False
in_string = False
in_triple_string = False
in_char = False
escaped = False

i = 0
n = len(code)
while i < n:
    char = code[i]
    
    # Track line/col
    if char == "\n":
        line_num += 1
        col_num = 1
    else:
        col_num += 1

    # Check comments and strings
    if in_block_comment:
        if char == "*" and i + 1 < n and code[i+1] == "/":
            in_block_comment = False
            i += 2
            col_num += 1
            continue
        i += 1
        continue
    if in_line_comment:
        if char == "\n":
            in_line_comment = False
        i += 1
        continue
    if in_triple_string:
        if char == "\"" and i + 2 < n and code[i+1] == "\"" and code[i+2] == "\"":
            in_triple_string = False
            i += 3
            col_num += 2
            continue
        i += 1
        continue
    if in_string:
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == "\"":
            in_string = False
        i += 1
        continue
    if in_char:
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == "'":
            in_char = False
        i += 1
        continue

    # Start of comment or string
    if char == "/" and i + 1 < n and code[i+1] == "*":
        in_block_comment = True
        i += 2
        col_num += 1
        continue
    if char == "/" and i + 1 < n and code[i+1] == "/":
        in_line_comment = True
        i += 2
        col_num += 1
        continue
    if char == "\"" and i + 2 < n and code[i+1] == "\"" and code[i+2] == "\"":
        in_triple_string = True
        i += 3
        col_num += 2
        continue
    if char == "\"":
        in_string = True
        i += 1
        continue
    if char == "'":
        in_char = True
        i += 1
        continue

    # Braces tracking
    if char == "{":
        stack.append((line_num, col_num))
    elif char == "}":
        if stack:
            stack.pop()
        else:
            print(f"Extra closing brace at line {line_num}, col {col_num}")

    i += 1

print(f"Done parsing. Stack size: {len(stack)}")
if stack:
    print("Unmatched opening braces:")
    for line, col in stack[-20:]:
        print(f"  Line {line}, col {col}: {lines[line-1][:100].strip()}")
