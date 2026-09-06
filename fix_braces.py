import re

with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'r') as f:
    text = f.read()

# Replace the block of closing braces
text = text.replace(
    '''                        }
                    }
                }
            }
        }
    }
}
}
// STAGE 6:''',
    '''                        }
                    }
                }
            }
        }
    }
}
// STAGE 6:'''
)

text = text.replace(
    '''                        }
                    }
                }
            }
        }
    }
}
// STAGE 6: COMMUNICATE - CONOR QUINN LIVE SCENARIO''',
    '''                        }
                    }
                }
            }
        }
    }
}
// STAGE 6: COMMUNICATE - CONOR QUINN LIVE SCENARIO'''
)


with open('app/src/main/java/com/example/ui/shadowing/ShadowingScreen.kt', 'w') as f:
    f.write(text)
