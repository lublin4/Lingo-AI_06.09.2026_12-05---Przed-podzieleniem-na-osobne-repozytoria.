import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    # If we find a top-level function or annotation that precedes a top-level function
    # We should insert a `}` right before it, but only if it's not the first function in the file
    if line.startswith('@Composable') or line.startswith('fun ') or line.startswith('@OptIn'):
        # Check if the previous line was also an annotation, to avoid adding multiple braces
        prev_is_annotation = False
        if i > 0:
            for j in range(i-1, -1, -1):
                if lines[j].strip() == '':
                    continue
                if lines[j].startswith('@'):
                    prev_is_annotation = True
                break
        
        # Don't add brace if it's the very first top-level element
        is_first = True
        for j in range(i-1, -1, -1):
            if lines[j].startswith('fun '):
                is_first = False
                break
                
        if not prev_is_annotation and not is_first:
            new_lines.append('}\n\n')
            
    new_lines.append(line)

new_lines.append('\n}\n') # for the very last function

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.writelines(new_lines)

